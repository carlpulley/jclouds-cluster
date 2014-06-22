# Copyright (C) 2013  Carl Pulley
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.

include_recipe "java"

user node[:cluster][:service] do
  action :create
end

require 'socket'

ipaddress = Socket.ip_address_list.find { |a| a.ipv4? ? !(a.ipv4_loopback?)  :  !(a.ipv6_sitelocal? || a.ipv6_linklocal? || a.ipv6_loopback?) }

unless ipaddress.nil?
  hostsfile_entry ipaddress.ip_address do
    hostname node[:cluster][:role]
    unique true
    action :create
  end
end

cookbook_file "/tmp/#{node[:cluster][:service]}-#{node[:cluster][:version]}.deb" do
  source "#{node[:cluster][:service]}-#{node[:cluster][:version]}.deb"
  owner "root"
  group "root"
  mode "0444"
end

package node[:cluster][:service] do
  provider Chef::Provider::Package::Dpkg
  source "/tmp/#{node[:cluster][:service]}-#{node[:cluster][:version]}.deb"
  action :install
end

directory "/usr/share/#{node[:cluster][:service]}/config" do
  owner "root"
  group "root"
  mode "0755"
  action :create
end

template "/usr/share/#{node[:cluster][:service]}/config/akka-remote.conf" do
  source "akka-remote.conf.erb"
  owner "root"
  group "root"
  mode "0644"
  variables({ :ipaddress => ipaddress.ip_address })
end

template "/usr/share/#{node[:cluster][:service]}/config/akka-cluster.conf" do
  source "akka-cluster.conf.erb"
  owner "root"
  group "root"
  mode "0644"
end

service node[:cluster][:service] do
  provider Chef::Provider::Service::Upstart
  supports :restart => true, :start => true, :stop => true
  action [:enable, :restart]
end
