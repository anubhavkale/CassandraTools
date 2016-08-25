# Chef Recipe to restore system keyspaces

cookbook_file "/tmp/br.jar" do
  source "br.jar"
  mode 0755
end

cookbook_file "/tmp/restore-system.sh" do
  source "restore-system.sh"
  mode 0755
end

# Get the cassandra cluster name from yaml file (remove all special characters and make it lowercase)
 clustername=`(cat /etc/dse/cassandra/cassandra.yaml| sed -n 's/cluster_name://p' |  tr -dc '[:alnum:]' | tr '[:upper:]' '[:lower:]')`

cron 'backup_cassandra' do
  hour '*/2'
  minute '0'
  command "java -cp /tmp/br.jar Program backup #{chef_vault_item('credentials', "cassandra_#{clustername}")['nodetool_user']} #{chef_vault_item('credentials', "cassandra_#{clustername}")['nodetool_password']} #{node.chef_environment.downcase} #{chef_vault_item('credentials', "alertbox")['url']} #{chef_vault_item('credentials', "alertbox")['applicationid']} /mnt/cassandra /datadisks/disk1 backup 3d > /tmp/rsync.txt 2>&1"
  action :delete
end

ruby_block "set_backup_attribute_false" do
  block do
	node.normal['cassandra']['is_backup_needed']  = false
	node.save()
  end
end

file "/tmp/lockfilesystembr" do
   action :create_if_missing
   notifies :run, 'execute[restore_system_keyspaces]', :immediately
end

execute 'restore_system_keyspaces' do
   command "sh /tmp/restore-system.sh #{node.chef_environment.downcase} #{chef_vault_item('credentials', "alertbox")['url']} #{chef_vault_item('credentials', "alertbox")['applicationid']} #{chef_vault_item('credentials', "cassandra_#{clustername}")['cqlsh_user']} #{chef_vault_item('credentials', "cassandra_#{clustername}")['cqlsh_password']} > /tmp/restore_system.txt"
   sensitive true
   action :nothing
end
