# Chef recipe to setup backups as a cron task

cookbook_file "/tmp/br.jar" do
  source "br.jar"
  mode 0755
end

# Get the cassandra cluster name from yaml file (remove all special characters and make it lowercase)
clustername=`(cat /etc/dse/cassandra/cassandra.yaml| sed -n 's/cluster_name://p' |  tr -dc '[:alnum:]' | tr '[:upper:]' '[:lower:]')`


if (!node['cassandra'].nil? && !node['cassandra']['is_backup_needed'].nil? && node['cassandra']['is_backup_needed'] == true)
  cron 'backup_cassandra' do
    hour '*/2'
    minute '0'
    command "timeout -k 110m 110m java -cp /tmp/br.jar Program backup #{chef_vault_item('credentials', "cassandra_#{clustername}")['nodetool_user']} #{chef_vault_item('credentials', "cassandra_#{clustername}")['nodetool_password']} #{node.chef_environment.downcase} #{chef_vault_item('credentials', "alertbox")['url']} #{chef_vault_item('credentials', "alertbox")['applicationid']} /mnt/cassandra /datadisks/disk1 backup 3d > /tmp/rsync.txt 2>&1 && find /mnt/cassandra/data -name 'backup' -type d -exec rm -rf {} \\;"
    action :create
  end
else
  cron 'backup_cassandra' do
    hour '*/2'
    minute '0'
    command "timeout -k 110m 110m java -cp /tmp/br.jar Program backup #{chef_vault_item('credentials', "cassandra_#{clustername}")['nodetool_user']} #{chef_vault_item('credentials', "cassandra_#{clustername}")['nodetool_password']} #{node.chef_environment.downcase} #{chef_vault_item('credentials', "alertbox")['url']} #{chef_vault_item('credentials', "alertbox")['applicationid']} /mnt/cassandra /datadisks/disk1 backup 3d > /tmp/rsync.txt 2>&1 && find /mnt/cassandra/data -name 'backup' -type d -exec rm -rf {} \\;"
    action :delete
  end
end
