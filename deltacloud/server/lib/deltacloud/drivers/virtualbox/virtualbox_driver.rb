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

require 'tmpdir'

module Deltacloud
  module Drivers
    module Virtualbox
      class VirtualboxDriver < Deltacloud::BaseDriver

        VBOX_MANAGE_PATH = '/usr/bin'

        feature :instances, :user_name
        feature :instances, :authentication_password
        feature :instances, :user_files
        feature :instances, :user_data
        feature :images, :user_name
        feature :keys, :import_key
        feature :storage_volumes, :volume_name
        feature :storage_volumes, :volume_description

        define_hardware_profile 'micro' do
          cpu           1
          memory        512
          storage       1
          architecture  `uname -m`.strip
        end

        define_hardware_profile 'small' do
          cpu           1
          memory        1024
          storage       1
          architecture  `uname -m`.strip
        end
        
        define_hardware_profile 'medium' do
          cpu           2
          memory        2048
          storage       1
          architecture  `uname -m`.strip
        end
 
        define_hardware_profile 'large' do
          cpu           4
          memory        4096
          storage       1
          architecture  `uname -m`.strip
        end
 
        define_hardware_profile 'custom' do
          architecture  `uname -m`.strip
        end

        define_instance_states do
          start.to(:pending)    .on(:start)
          start.to(:finish)     .on(:destroy)
          pending.to(:running)  .automatically
          running.to(:running)  .on(:reboot)
          running.to(:stopping) .on(:stop)
          running.to(:finish)   .on(:destroy)
          stopping.to(:stopped) .automatically
          stopped.to(:running)  .on(:start)
          stopped.to(:finish)   .on(:destroy)
        end

        def images(credentials, opts = nil)
          images = convert_images(vbox_client('list vms'))
          images = filter_on( images, :id, opts )
          images = filter_on( images, :architecture, opts )
          images.sort_by{|e| [e.owner_id, e.description]}
        end

        def destroy_image(credentials, image_id)
          vbox_client("unregistervm '#{image_id}' --delete")
        end

        def realms(credentials, opts={})
          return [Realm.new({
            :id => 'local',
            :name => 'localhost',
            :limit => 'unlimited',
            :state => 'AVAILABLE'
          })]
        end
 
        # TODO: use user_data when booting - see http://cloudinit.readthedocs.org/en/latest/topics/datasources.html#no-cloud
        def create_instance(credentials, image_id, opts)
          instance = instances(credentials, { :image_id => image_id }).first
          name = opts[:name] || "#{instance.name} - #{Time.now.to_i}"

          # Create new virtual machine, UUID for this machine is returned
          vm=vbox_client("createvm --name '#{name}' --register")
          new_uid = vm.split("\n").select { |line| line=~/^UUID/ }.first.split(':').last.strip
          
          # Add Hardware profile to this machine
          profile = hardware_profile(credentials, opts[:hwp_id] || "small")
          cpu = profile.cpu
          memory = profile.memory
          ostype = profile.id == 'microsoft' ? "Windows" : "Linux"
 
          vbox_client("modifyvm '#{new_uid}' --ostype #{ostype} --memory #{memory} --vram 16 --nic1 nat --cableconnected1 on --cpus #{cpu}")

          # Add storage
          # This will 'reuse' existing image
          location = instance_volume_location(instance.id)
          new_location = File.join(File.dirname(location), name+'.vdi')

          # This need to be in a fork, because it takes some time with large images
          fork do
            vbox_client("clonehd '#{location}' '#{new_location}' --format VDI")
            vbox_client("storagectl '#{new_uid}' --add ide --name '#{name}'-hd0 --controller PIIX4")
            vbox_client("storageattach '#{new_uid}' --storagectl '#{name}'-hd0 --port 0 --device 0 --type hdd --medium '#{new_location}'")

            add_user_data(new_uid, opts[:user_data]) if opts[:user_data]
          end
          
          convert_instance(new_uid, image_id)
        end

        # TODO: run_on_instance?

        def reboot_instance(credentials, id)
          vbox_client("controlvm '#{id}' reset")
        end
  
        def destroy_instance(credentials, id)
          vbox_client("controlvm '#{id}' poweroff")
          vbox_client("unregistervm '#{id}' --delete")
        end

        def start_instance(credentials, id)
          instance = instances(credentials, { :id => id }).first
          # Handle 'pause' and 'poweroff' state differently
          if 'START'.eql?(instance.state)
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
            hdd_id = raw_image[bootdisk(raw_image)]
            next unless hdd_id
            volume = convert_volume(vbox_client("showhdinfo '#{hdd_id}'"))
            volumes << volume unless volume.nil?
          end
          filter_on( volumes, :id, opts )
        end

        def create_storage_volume(credentials, opts=nil)
          # TODO:
        end

        def detach_storage_volume(credentials, opts={})
          # TODO:
        end

        def create_storage_snapshot(credentials, opts={})
          # TODO:
        end
 
        private

        def genisoimage(src)
          iso = "#{Dir.mktmpdir}/nocloud.iso"
          case RbConfig::CONFIG['target_os']
          when /^darwin/
            `hdiutil makehybrid -iso -joliet -o #{iso} #{src}`
          else
            `genisoimage -output #{iso} -volid cidata -joliet -rock #{src}`
          end
          iso
        end

        def add_user_data(uuid, user_data)
          Dir.mktmpdir do |dir|
            open("#{dir}/meta-data", "w") { |fd| fd.write("local-hostname: localhost") }
            open("#{dir}/user-data", "w") { |fd| fd.write(user_data) }
            iso = genisoimage(dir)
            raw_image = convert_image(vbox_vm_info(uuid))
            name = raw_image.select { |k, v| /^storagecontrollertype\d$/ =~ k && ["PIIX3", "PIIX4", "ICH6"].member?(v) }.map { |k, v| k }.first
            if name.empty?
              controller = "IDE Controller"
              vbox_client("storagectl '#{uuid}' --name '#{controller}' --add ide")
            else
              n = name[-1]
              controller = raw_image[:"storagecontrollername#{n}"]
            end
            slot = raw_image.select { |k,v| Regexp.new("^#{controller}-\\d-\\d$") =~ k && v == "none" }.map { |k, v| k }.first.to_s
            unless slot.nil?
              device = slot.split("-")[-1]
              port = slot.split("-")[-2]
              vbox_client("storageattach '#{uuid}' --storagectl '#{controller}' --port #{port} --device #{device} --type dvddrive --medium '#{iso}'")
            end
          end
        end
 
        def vbox_client(cmd)
          `#{VBOX_MANAGE_PATH}/VBoxManage -q #{cmd}`.strip
        end
 
        def vbox_vm_info(uid)
          vbox_client("showvminfo --machinereadable '#{uid}'")
        end
 
        def vm_hdd_uses(uid)
          imgs = []
          vbox_client("list hdds -l").split(/\n\n/).each do |image|
            volume = convert_raw_volume(image)
            imgs << volume[:uuid] if volume[:"in-use-by-vms"] == uid
          end
          imgs
        end

        def convert_instance(image_id, base_image_id = nil)
          raw_image = convert_image(vbox_vm_info(image_id))
          volume = convert_volume(vbox_get_volume(raw_image[bootdisk(raw_image)]))
          hwprof = self.class.hardware_profiles.find { |hwp| hwp.property("cpu") && hwp.property("cpu").value == raw_image[:cpus].to_i && hwp.property("memory") && hwp.property("memory").value == raw_image[:memory].to_i }
          if hwprof.nil?
            profile = InstanceProfile.new("custom")
            profile.cpu = raw_image[:cpus]
            profile.memory = raw_image[:memory]
          else
            profile = InstanceProfile.new(hwprof.id)
          end
          profile.storage = volume.capacity if volume

          Instance.new({
            :id => image_id,
            :image_id => base_image_id || image_id, # FIXME: need to save base_image_id upon creation
            :name => raw_image[:name],
            :state => convert_state(raw_image[:vmstate], volume),
            :owner_id => ENV['USER'] || ENV['USERNAME'] || 'nobody',
            :realm_id => 'local',
            :public_addresses => vbox_get_ip(image_id),
            :private_addresses => vbox_get_ip(image_id),
            :actions => instance_actions_for(convert_state(raw_image[:vmstate], volume)),
            :instance_profile => profile,
            :storage_volumes => vm_hdd_uses(image_id).map { |vol| { vol => image_id } },
            :launch_time => raw_image[:vmstatechangetime]
          })
        end

        def convert_instances(instances)
          vms = []
          instances.split("\n").each do |image|
            image_id = image.match(/^\"(.+)\" \{(.+)\}$/).to_a.last
            vms << convert_instance(image_id)
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
            return [InstanceAddress.new(raw_ip)]
          end
        end
 
        def vbox_get_volume(uuid)
          vbox_client("showhdinfo #{uuid}")
        end
 
        def convert_state(state, volume)
          if volume.nil? 
            'PENDING'
          else
            case state.strip.upcase
            when 'POWEROFF'
              'START'
            when 'ABORTED'
              'STOPPED'
            when 'SAVED'
              'STOPPED'
            when 'PAUSED'
              'STOPPED'
            else
              state.strip.upcase
            end
          end
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
          raw_image = convert_image(vbox_vm_info(instance_id))
          volume_uuid = raw_image[bootdisk(raw_image)]
          convert_raw_volume(vbox_get_volume(volume_uuid))[:location]
        end
 
        def convert_raw_volume(volume)
          vol = {}
          volume.split("\n").each do |v|
            v = v.split(':')
            next unless v.first
            key = v.first.strip.downcase.gsub(/\W/, '-')
            value = v.last.strip
            vol[:"#{key}"] = value
            if key == "in-use-by-vms" && value[-1] == ')'
              vol[:"#{key}"] = value[0...-1]
            end
          end
          return vol
        end
 
        def convert_volume(volume)
          vol = convert_raw_volume(volume)
          return nil unless vol[:uuid]
          c = vol["capacity".to_sym].split(' ')
          toGB = case c[1]
          when "KBytes"
            1000000
          when "MBytes"
            1000
          else
            1
          end
          capacity = c[0].to_f / toGB
          StorageVolume.new(
            :id => vol[:uuid],
            :created => Time.now,
            :state => 'AVAILABLE',
            :capacity => '%.2f' % capacity,
            :instance_id => vol["in-use-by-vms".to_sym],
            :device => vol[:type],
            :realm_id => "local"
          )
        end

        def convert_images(images)
          vms = []
          images.split("\n").each do |image|
            image_id = image.match(/^\"(.+)\" \{(.+)\}$/).to_a.last
            raw_image = convert_image(vbox_vm_info(image_id))
            volume = convert_volume(vbox_get_volume(raw_image[bootdisk(raw_image)]))
            next unless volume
            capacity = ", #{volume.capacity} HDD"
            vms << Image.new(
              :id => raw_image[:uuid],
              :name => raw_image[:name],
              :state => raw_image[:vmstate],
              :description => "#{raw_image[:memory]} MB RAM, #{raw_image[:cpu] || 1} CPU#{capacity}",
              :owner_id => ENV['USER'] || ENV['USERNAME'] || 'nobody',
              :architecture => `uname -m`.strip,
              :hardware_profiles => self.class.hardware_profiles.select { |hwp| hwp.name != "custom" }
            )
          end
          return vms
        end

        def bootdisk(image)
          controller_names = image.keys.select { |k| /^storagecontrollername\d$/ =~ k.to_s }
          for name in controller_names
            controllers = image.select { |k, v| Regexp.new("^#{image[name].downcase}-\\d-\\d$") =~ k && v != "none" }
            for controller in controllers.keys
              slot = controller[-3..-1]
              return "#{image[name].downcase}-imageuuid-#{slot}".to_sym if /\.vdi$/ =~ image[controller] || /\.vmdk$/ =~ image[controller]
            end
          end
          nil
        end

      end
    end
  end
end
