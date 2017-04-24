# CassandraTools
Tools to operate on Cassandra at production scale

BackupRestore

The BackupRestore folder contains tool to backup Cassandra data on a per node basis, and restore. The overall approach is as follows:

1] Attach a persistent disk to every node in Cassandra cluster

2] Use the program to backup to this disk periodically using Cron. The "copy" itself is performed using Linux rsync command.

3] To restore, use the program's "restore" command on a per node basis. First restore the system keyspaces so you can take the live traffic in, and then resore the non-system (your data) keyspaces. More details are below.

To use this tool, build the code from within backuprestore and name the generated jar br.jar

Backup:

sudo java -cp br.jar Program backup <nodetool_username> <nodetool_password> <Cassandra Data Directory> <Backup Disk Directory> <What to name the snapshot> <When to timeout the rsync command. Use the format for Unix timeout command here (e.g. 3d)>

Notes: 

1. The program will look for /data under <Cassandra Data Directory> to look for the data to be backed up.

2. The snapshot can be any short name (10 letter limit)

3. The typical timeout for the rsync command would be less than your cron recurrence schedule. 

Restore:

sudo java -cp br.jar Program restore <Cassandra Data Directory> <Backup Disk Location> <Name of snapshot used while taking backup> <rsync command timeout> systemks true

This will restore the following keyspaces so that the node can start and accept traffic. 

"system", "system_auth", "system_traces", "dse_security", "dse_system"

Once this operation completes on all nodes, use the command below to restore your data keyspaces

sudo java -cp br.jar Program restore <Cassandra Data Directory> <Backup Disk Location> <Name of snapshot used while taking backup> <rsync command timeout> nonsystemks

This operation will take time proportional to your data size. 

Further Notes:

1. If the cluster topology changes after the backups were taken (nodes were added), the restore operation won't be accurate. A backup from all nodes in the cluster must be done atleast once in order for restore to complete correctly. 

2. This tool is tested on nodes holding ~500GB data on a SSD and backed to HDD. While first backup takes a while, rsync ensures that the backups after the first are incremental in nature. 





