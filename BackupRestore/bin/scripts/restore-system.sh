# Script to restore system keyspaces
echo "Stopping DSE"
service dse stop
echo "Stopped DSE. Removing commitlog, saved_cache and data"
rm -rf /mnt/cassandra/commitlog/*
rm -rf /mnt/cassandra/saved_caches/*
rm -rf /mnt/cassandra/data/*
echo "Removed all. Running restore"
java -cp /tmp/br.jar Program restore $1 $2 $3 /mnt/cassandra /datadisks/disk1 backup 3d systemks true
echo "Finished restore. Starting DSE"
service dse start
echo "Started DSE. Waiting 2m before trying cqlsh"
sleep 2m
cluster_name=$(cat /etc/dse/cassandra/cassandra.yaml| sed -n 's/cluster_name://p' | sed 's/^ *//g' | sed 's/ *$//g')
cqlsh -u $4 -p $5 -e 'desc cluster' > /tmp/cqlsh.txt
if grep -q "$cluster_name" tmp/cqlsh.txt 2>/dev/null
then
echo "SUCCESS: CqlSh run success"
exit 0
else
echo "ERROR: CqlSh run failed"
exit 1
fi
