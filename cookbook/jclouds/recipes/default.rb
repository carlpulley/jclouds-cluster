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

remote_directory "#{node[:jclouds][:deploy_dir]}/#{node[:jclouds][:service]}" do
  source "jclouds-deploy"
  purge true
  owner 'root'
  group 'root'
  mode 0755
  files_mode 0644
end

bash "create-service-log" do
  code <<-EOF
  touch /var/log/#{node[:jclouds][:service]}.log
  chown #{node[:jclouds][:uid]}:adm /var/log/#{node[:jclouds][:service]}.log
  EOF
end

service node[:jclouds][:service] do
  provider Chef::Provider::Service::Upstart
  supports :restart => true, :start => true, :stop => true
end

template "/etc/init/#{node[:jclouds][:service]}.conf" do
  source "jclouds.conf.erb"
  owner 'root'
  group 'root'
  mode 0644
end

service node[:jclouds][:service] do
  action [:enable, :start]
end
