import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// This is coded in Java so that its possible to hook up custom alerting to backup / restore. 
// /mnt/cassandra holds the real data, and /datadisks/disk1 is the disk holding backups.
// Backup: 
// sudo java -cp /tmp/br.jar Program backup nodetool_user nodetool_password Customerbox-Prod /mnt/cassandra /datadisks/disk1 backup 3d 
// Restore:
// sudo java -cp /tmp/br.jar Program restore /mnt/cassandra /datadisks/disk1 backup 3d systemks true
// sudo java -cp /tmp/br.jar Program restore /mnt/cassandra /datadisks/disk1 backup 3d nonsystemks
public class Program {
    private static String backupOrRestore;
    private static String cassandraDir;
    private static String cassandraDataDir;
    private static String backupDir;
    private static String backupDataDir;
    private static String backupSnapshotName;
    private static String rsyncTimeout;
    private static String keyspaces;
    private static String nodetoolUser;
    private static String nodetoolPassword;
    private static String bootstrapNonSystemKeyspaces;
    
    private static String SYSTEMKS = "systemks";
    private static String NONSYSTEMKS = "nonsystemks";

    private static Set<String> SystemKeyspaces = new HashSet<String>(Arrays.asList("system", "system_auth", "system_traces", "dse_security", "dse_system"));

    public static void main(String[] args) throws Exception {
        Helper.log("Started");
        long startTime = System.currentTimeMillis();
        
        backupOrRestore = args[0];
        
        if (backupOrRestore.equals("backup")) {
            nodetoolUser = args[1];
            nodetoolPassword = args[2];
            cassandraDir = args[3];
            backupDir = args[4];
            backupSnapshotName = args[5];
            rsyncTimeout = args[6];

            cassandraDataDir = cassandraDir + File.separator + "data";
            backupDataDir = backupDir + File.separator + "data";
            logParams();
            backup();
        } else if (backupOrRestore.equals("restore")) {
            cassandraDir = args[1];
            backupDir = args[2];
            backupSnapshotName = args[3];
            rsyncTimeout = args[4];
            keyspaces = args.length >= 6 ? args[5] : "";
            bootstrapNonSystemKeyspaces = args.length >= 7 ? args[6] : "false";
            
            if(bootstrapNonSystemKeyspaces.equalsIgnoreCase("true") && !keyspaces.equalsIgnoreCase(SYSTEMKS))
            {
                Helper.log("Error: You can only use bootstrapNonSystemKeyspaces with systemks");
                return;
            }
            
            cassandraDataDir = cassandraDir + File.separator + "data";
            backupDataDir = backupDir + File.separator + "data";
            logParams();
            restore();
        } else {
            Helper.log("Tough Luck! Possible options are backup and restore");
        }

        long endTime = System.currentTimeMillis();
        double timeTaken = (endTime - startTime) / (1000 * 60);
        Helper.log("Completed. Time " + timeTaken + " minutes ");
    }

    private static void logParams() {
        Helper.log("BackupOrRestore:" + backupOrRestore + " Cassandra Dir:" + cassandraDir
                + " Backup Dir:" + backupDir + " Backup SnapshotName:" + backupSnapshotName + " RsyncTimeout:"
                + rsyncTimeout + " Keyspaces:" + keyspaces + " BootstrapNonSystemKeyspaces:"
                + bootstrapNonSystemKeyspaces);
    }

    public static void backup() throws Exception {
        try {
            Helper.command("nodetool -u " + nodetoolUser + " -pw " + nodetoolPassword + " clearsnapshot -t " + backupSnapshotName, true);
            Helper.command("nodetool -u " + nodetoolUser + " -pw " + nodetoolPassword + " snapshot -t " + backupSnapshotName, true);
            boolean result = Helper.command("timeout -k " + rsyncTimeout + " " + rsyncTimeout + " rsync -azP --delete "
                    + cassandraDataDir + " --include '*/'  --include 'snapshots/***' --exclude '*' " + backupDir);
            Helper.log("Backup Success: " + result);
        } catch (Exception ex) {
            Helper.log(ex);
        }
        finally
        {
            Helper.command("find " + cassandraDataDir + " -name '" +  backupSnapshotName + "' -type d -exec rm -rf {} \\;");
        }
    }

    public static void restore() throws Exception {
        try {
            
            Set<String> keyspaceList = new HashSet<String>();
            
            if (keyspaces.equalsIgnoreCase(SYSTEMKS))
            {
                keyspaceList = SystemKeyspaces;
                performRsyncForKeyspaces(keyspaceList);
            }
            else if (keyspaces.equalsIgnoreCase(NONSYSTEMKS))
            {
                // RSync all by system keyspaces (Typically Step 2 in Restore)
                String rsyncCommand = "";
                for (String keyspace : SystemKeyspaces) {
                    rsyncCommand = rsyncCommand + " --exclude '" + keyspace + "/***' ";
                }
                
                Helper.command("timeout -k " + rsyncTimeout + " " + rsyncTimeout + " rsync -azP "
                        + backupDataDir + rsyncCommand + cassandraDir);
                Helper.log("Finished rsync for non system keyspaces");
            }
            else
            {
                keyspaceList = new HashSet<String>(Arrays.asList(keyspaces.split(",")));
                performRsyncForKeyspaces(keyspaceList);
            }
        } catch (Exception ex) {
            Helper.log(ex);
            throw ex;
        }

        Helper.log("Moving snapshots to actual location");

        // Now move snapshots two levels up.
        File dataDir = new File(cassandraDataDir);
        File[] ksDirs = dataDir.listFiles();

        for (File ksDir : ksDirs) {
            File[] tableDirs = ksDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File current, String name) {
                    return new File(current, name).isDirectory();
                }
            });

            for (File tableDir : tableDirs) {

                File[] snapshotsDirs = tableDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File current, String name) {
                        return new File(current, name).isDirectory();
                    }
                });

                for (File snapshotDir : snapshotsDirs) {

                    File[] backupDirs = snapshotDir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File current, String name) {
                            return new File(current, name).isDirectory() && name.equals(backupSnapshotName);
                        }
                    });

                    if (backupDirs.length != 1) {
                        Helper.log("No backup found for table " + tableDir.getAbsolutePath());
                        continue;
                    }

                    if (backupDirs[0].listFiles().length > 0) {
                        try {
                            Helper.command("mv " + backupDirs[0].getAbsolutePath() + File.separator + "*.* " + tableDir.getAbsolutePath());
                            Helper.log("Finished moving " + tableDir.getAbsolutePath());
                        } catch (Exception ex) {
                            Helper.log(ex);
                        }
                    }
                }
            }
        }

        Helper.log("Completed moving snapshots to actual location");
    
        // Bootstrap non system keyspaces if asked for
        // This moves one SS Table from every keyspace to the target
        // with file number set to the highest file number + 1 
        // This makes sure that when node starts taking live traffic, the
        // file names don't collide and there is no data loss
        if (bootstrapNonSystemKeyspaces.equalsIgnoreCase("true")) {
            Helper.log("Started bootstrapping non system keyspaces");
            File backupdataDir = new File(backupDataDir);
            File[] backupKsDirs = backupdataDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File current, String name) {
                    return new File(current, name).isDirectory() && !SystemKeyspaces.contains(name);
                }
            });

            for (File ksDir : backupKsDirs) {
                File[] tableDirs = ksDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File current, String name) {
                        return new File(current, name).isDirectory();
                    }
                });

                for (File tableDir : tableDirs) {
                    File[] snapshotsDirs = tableDir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File current, String name) {
                            return new File(current, name).isDirectory();
                        }
                    });

                    for (File snapshotDir : snapshotsDirs) {
                        File[] backupDirs = snapshotDir.listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File current, String name) {
                                return new File(current, name).isDirectory() && name.equals(backupSnapshotName);
                            }
                        });

                        if (backupDirs.length != 1) {
                            Helper.log("No backup found for table " + tableDir.getAbsolutePath());
                            continue;
                        }

                        File[] backupFiles = backupDirs[0].listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File current, String name) {
                                return new File(current, name).isFile() && !name.equalsIgnoreCase("manifest.json");
                            }
                        });
                        
                        long maxNumber = 0;
                        long currentNumber;
                        
                        // Find the file with largest number in it
                        for (File backupFile : backupFiles) {
                            currentNumber = Helper.getSSTableNumber(backupFile.getName());
                            if (currentNumber >= maxNumber) {
                                maxNumber = currentNumber;
                            }
                        }

                        Helper.log("Table: " + tableDir.getName() + " ,Max SS Table Number: " + maxNumber);
                        
                        String targetDir = cassandraDataDir + File.separator + ksDir.getName() + File.separator + tableDir.getName();
                        Helper.command("mkdir -p " + targetDir);

                        for (File backupFile : backupFiles) {
                            String originalName = backupFile.getName();
                            currentNumber = Helper.getSSTableNumber(originalName);

                            if (currentNumber == maxNumber) {
                                Helper.command("mv " + backupFile.getAbsolutePath() + " " + targetDir + File.separator);
                                Helper.log("Finished moving " + originalName + " to " + targetDir + File.separator);
                            }
                        }
                    }
                }
            }
            
            Helper.log("Finished bootstrapping non system keyspaces");
        }
        
        Helper.log("Changing ownership of cassandra");
        Helper.command("chown cassandra:cassandra -R " + cassandraDir);
        Helper.log("Completed changing ownership of cassandra");
    }

    private static void performRsyncForKeyspaces(Set<String> keyspaceList) throws Exception {
        // Note no delete in rsync param because node is taking live traffic
        // Also, we do one keyspace at a time so that we can order them if necessary
        if (!keyspaceList.isEmpty()) {
            for (String keyspace : keyspaceList) {
                Helper.command("timeout -k " + rsyncTimeout + " " + rsyncTimeout + " rsync -azP "
                        + backupDataDir + " --include '*/' --include '" + keyspace + "/***' --exclude '*' "
                        + cassandraDir);
            }
        } else {
            Helper.log("Started rsync for all");
            Helper.command("timeout -k " + rsyncTimeout + " " + rsyncTimeout + " rsync -azP " + backupDataDir + " " + cassandraDir);
            Helper.log("Finished rsync for all");
        }
    }
}