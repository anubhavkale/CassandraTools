# Chef recipe to restore all keyspaces after restoring system keyspaces 

cookbook_file "/tmp/br.jar" do
  source "br.jar"
  mode 0755
end

cookbook_file "/tmp/restore-others.sh" do
  source "restore-others.sh"
  mode 0755
end

file "/tmp/lockfileothersbr" do
   action :create_if_missing
   notifies :run, 'execute[restore_other_keyspaces]', :immediately
end

# Get the cassandra cluster name from yaml file (remove all special characters and make it lowercase)
 clustername=`(cat /etc/dse/cassandra/cassandra.yaml| sed -n 's/cluster_name://p' |  tr -dc '[:alnum:]' | tr '[:upper:]' '[:lower:]')`

execute 'restore_other_keyspaces' do
   command "sh /tmp/restore-others.sh #{node.chef_environment.downcase} #{chef_vault_item('credentials', "alertbox")['url']} #{chef_vault_item('credentials', "alertbox")['applicationid']} #{chef_vault_item('credentials', "cassandra_#{clustername}")['cqlsh_user']} #{chef_vault_item('credentials', "cassandra_#{clustername}")['cqlsh_password']} > /tmp/restore_others.txt"
   sensitive true
   action :nothing
   notifies :run, 'ruby_block[set_backup_attribute_true]', :immediately
end

ruby_block "set_backup_attribute_true" do
  block do
	node.normal['cassandra']['is_backup_needed']  = true
	node.save()
	action :nothing
  end
end
