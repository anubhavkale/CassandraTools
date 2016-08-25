import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class Helper {
    public static boolean command(final String cmdline, final String directory, boolean sensitive) throws Exception {
            
            if (!sensitive)
            {
                log(cmdline);
            }
            
            Process process = new ProcessBuilder(new String[] { "bash", "-c", cmdline }).redirectErrorStream(true).directory(new File(directory)).start();
            printProcessOutput(process);
            
            // This won't wait eternally since we use timeout as part of command
            if (process.waitFor() != 0)
            { 
                System.out.println(cmdline + " did not finish successfully.");
                return false;
            }
            
            return true;
    }
    
    public static boolean command(final String cmdline) throws Exception {
        return command(cmdline, "/tmp", false);
    }
    
    public static boolean command(final String cmdline, boolean sensitive) throws Exception {
        return command(cmdline, "/tmp", sensitive);
    }

    private static void printProcessOutput(Process process) throws IOException {
        ArrayList<String> output = new ArrayList<String>();
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = null;
        while ((line = br.readLine()) != null)
        {
            System.out.println(line);
            output.add(line);
        }
    }

    public static void log(String message)
    {
        String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
        System.out.println(timeStamp + " " + message);
    }
    
    public static void log(Exception ex)
    {
        String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
        System.out.println(timeStamp + " " + ex);
    }

    public static long getSSTableNumber(String originalName) {
        String[] splits = originalName.split("-");
        return Long.parseLong(splits[3]);
    }
}