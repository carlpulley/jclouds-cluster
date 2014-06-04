# Copyright (C) 2009  Red Hat, Inc.
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

# Based on Michal Fojtik's deltacloud-virtualbox-driver 
#   - see https://gitorious.org/deltacloud-devel/deltacloud-virtualbox-driver

# INSTALLATION:
# 1. You need VirtualBox and VBoxManage tool installed 
# 2. You need to setup some images manually inside VirtualBox
# 3. You need to install 'Guest Additions' to this images for metrics
# 4. You need a lot of hard drive space ;-)

module Deltacloud
  module Drivers
    module Virtualbox
      class VirtualboxDriver < Deltacloud::BaseDriver

        VBOX_MANAGE_PATH = '/usr/bin'

        feature :instances, :user_name
        feature :instances, :authentication_key
        feature :instances, :authentication_password
        feature :instances, :user_files
        feature :instances, :user_data
        feature :images, :user_name
        feature :keys, :import_key
        feature :storage_volumes, :volume_name
        feature :storage_volumes, :volume_description

        define_hardware_profile 'small' do
          cpu           1
          memory        0.5
          storage       1
          architecture  `uname -m`.strip
        end
        
        define_hardware_profile 'medium' do
          cpu           1
          memory        1
          storage       1
          architecture  `uname -m`.strip
        end
 
        define_hardware_profile 'large' do
          cpu           2
          memory        2
          storage       1
          architecture  `uname -m`.strip
        end

        define_instance_states do
          create.to( :pending)    .automatically
          pending.to(:start)      .automatically
          start.to( :running )    .on( :create )
          running.to( :running )  .on( :reboot )
          running.to( :paused )   .on( :stop )
          running.to(:finish)     .on( :destroy )
          paused.to( :running )   .on( :start )
          poweroff.to( :start )   .on( :start)
        end

        def images(credentials, opts = nil)
          images = convert_images(vbox_client('list vms'))
          images = filter_on( images, :id, opts )
          images = filter_on( images, :architecture, opts )
          images.sort_by{|e| [e.owner_id, e.description]}
        end

        def realms(credentials, opts={})
          return Realm.new({
            :id => 'local',
            :name => 'localhost',
            :limit => 100,
            :state => 'AVAILABLE'
          })
        end
 
        # TODO: use user_data when booting - see http://cloudinit.readthedocs.org/en/latest/topics/datasources.html#no-cloud
        def create_instance(credentials, image_id, opts)
          instance = instances(credentials, { :image_id => image_id }).first
          name = opts[:name] || "#{instance.name} - #{Time.now.to_i}"
 
          # Create new virtual machine, UUID for this machine is returned
          vm=vbox_client("createvm --name '#{name}' --register")
          new_uid = vm.split("\n").select { |line| line=~/^UUID/ }.first.split(':').last.strip
          
          # Add Hardware profile to this machine
          memory = 0.5
          ostype = "Linux"
          cpu = '1'
 
          if opts[:flavor_id]
            flavor = FLAVORS.select { |f| f.id == opts[:flavor_id]}.first
            memory = flavor.memory
            ostype = "Windows" if flavor.id == 'microsoft'
            cpu = "#{flavor.cpu}"
          end
 
          memory = ((memory*1.024)*1000).to_i
 
          vbox_client("modifyvm '#{new_uid}' --ostype #{ostype} --memory #{memory} --vram 16 --nic1 bridged --bridgeadapter eth0 --cableconnected1 on --cpus #{cpu}")
 
          # Add storage
          # This will 'reuse' existing image
          location = instance_volume_location(instance.id)
          new_location = File.join(File.dirname(location), name+'.vdi')
 
          # This need to be in fork, because it takes some time with large images
          fork do
            vbox_client("clonehd '#{location}' '#{new_location}' --format VDI")
            vbox_client("storagectl '#{new_uid}' --add ide --name '#{name}'-hd0 --controller PIIX4")
            vbox_client("storageattach '#{new_uid}' --storagectl '#{name}'-hd0 --port 0 --device 0 --type hdd --medium '#{new_location}'")
          end
        end

        # TODO: run_on_instance?

        def reboot_instance(credentials, id)
          vbox_client("controlvm '#{id}' reset")
        end
  
        def destroy_instance(credentials, id)
          vbox_client("controlvm '#{id}' poweroff")
        end

        def start_instance(credentials, id)
          instance = instances(credentials, { :id => id }).first
          # Handle 'pause' and 'poweroff' state differently
          if 'POWEROFF'.eql?(instance.state)
            vbox_client("startvm '#{id}'")
          else
            vbox_client("controlvm '#{id}' resume")
          end
        end

        def stop_instance(credentials, id)
          vbox_client("controlvm '#{id}' pause")
        end
   
        def instances(credentials, opts = nil)
          instances = convert_instances(vbox_client('list vms'))
          instances = filter_on( instances, :id, opts )
          instances = filter_on( instances, :state, opts )
          instances = filter_on( instances, :image_id, opts )
          instances
        end
 
        def storage_volumes(credentials, opts = nil)
          volumes = []
          require 'pp'
          instances(credentials, {}).each do |image|
            raw_image = convert_image(vbox_vm_info(image.id))
            hdd_id = raw_image['ide controller-imageuuid-0-0'.to_sym]
            next unless hdd_id
            volumes << convert_volume(vbox_client("showhdinfo '#{hdd_id}'"))
          end
          filter_on( volumes, :id, opts )
        end
 
        private
 
        def vbox_client(cmd)
          `#{VBOX_MANAGE_PATH}/VBoxManage -q #{cmd}`.strip
        end
 
        def vbox_vm_info(uid)
          vbox_client("showvminfo --machinereadable '#{uid}'")
        end
 
        def convert_instances(instances)
          vms = []
          instances.split("\n").each do |image|
            image_id = image.match(/^\"(.+)\" \{(.+)\}$/).to_a.last
            raw_image = convert_image(vbox_vm_info(image_id))
            volume = convert_volume(vbox_get_volume(raw_image['ide controller-imageuuid-0-0'.to_sym]))
            vms << Instance.new({
              :id => raw_image[:uuid],
              :image_id => volume ? raw_image[:uuid] : '',
              :name => raw_image[:name],
              :state => convert_state(raw_image[:vmstate], volume),
              :owner_id => ENV['USER'] || ENV['USERNAME'] || 'nobody',
              :realm_id => 'local',
              :public_addresses => vbox_get_ip(raw_image[:uuid]),
              :private_addresses => vbox_get_ip(raw_image[:uuid]),
              :actions => instance_actions_for(convert_state(raw_image[:vmstate], volume))
            })
          end
          return vms
        end
 
        # Warning: You need VirtualHost guest additions for this
        def vbox_get_ip(uuid)
          raw_ip = vbox_client("guestproperty get #{uuid} /VirtualBox/GuestInfo/Net/0/V4/IP")
          raw_ip = raw_ip.split(':').last.strip
          if raw_ip.eql?('No value set!')
            return []
          else
            return [raw_ip]
          end
        end
 
        def vbox_get_volume(uuid)
          vbox_client("showhdinfo #{uuid}")
        end
 
        def convert_state(state, volume)
          volume.nil? ? 'PENDING' : state.strip.upcase
        end
 
        def convert_image(image)
          img = {}
          image.split("\n").each do |i|
            si = i.split('=')
            img[:"#{si.first.gsub('"', '').strip.downcase}"] = si.last.strip.gsub('"', '')
          end
          return img
        end
 
        def instance_volume_location(instance_id)
          volume_uuid = convert_image(vbox_vm_info(instance_id))['ide controller-imageuuid-0-0'.to_sym]
          convert_raw_volume(vbox_get_volume(volume_uuid))[:location]
        end
 
        def convert_raw_volume(volume)
          vol = {}
          volume.split("\n").each do |v|
            v = v.split(':')
            next unless v.first
            vol[:"#{v.first.strip.downcase.gsub(/\W/, '-')}"] = v.last.strip
          end
          return vol
        end
 
        def convert_volume(volume)
          vol = convert_raw_volume(volume)
          return nil unless vol[:uuid]
          StorageVolume.new(
            :id => vol[:uuid],
            :created => Time.now,
            :state => 'AVAILABLE',
            :capacity => vol["logical-size".to_sym],
            :instance_id => vol["in-use-by-vms".to_sym],
            :device => vol[:type]
          )
        end
 
        def convert_images(images)
          vms = []
          images.split("\n").each do |image|
            image_id = image.match(/^\"(.+)\" \{(.+)\}$/).to_a.last
            raw_image = convert_image(vbox_vm_info(image_id))
            volume = convert_volume(vbox_get_volume(raw_image['ide controller-imageuuid-0-0'.to_sym]))
            next unless volume
            capacity = ", #{volume.capacity} HDD"
            vms << Image.new(
              :id => raw_image[:uuid],
              :name => raw_image[:name],
              :description => "#{raw_image[:memory]} MB RAM, #{raw_image[:cpu] || 1} CPU#{capacity}",
              :owner_id => ENV['USER'] || ENV['USERNAME'] || 'nobody',
              :architecture => `uname -m`.strip
            )
          end
          return vms
        end

      end
    end
  end
end
