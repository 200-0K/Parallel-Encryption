package menus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import classes.Encrypt;
import utils.Parallel;
import utils.ParallelParameters;
import utils.FileSystem;
import utils.UserInput;
import utils.Timer;

public class EncryptMenu implements IMenu{
    private enum MODE {
        Encrypt("Encrypt", "Encryption", true),
        Decrypt("Decrypt", "Decryption", false);

        private String mode;
        private String dirName;
        private boolean isEncryptMode;
        private String fileSuffix = ".ciph";
        private MODE(String mode, String dirName, boolean isEncryptMode) {
            this.mode = mode;
            this.dirName = dirName;
            this.isEncryptMode = isEncryptMode;
        }   
        public String getMode() {return mode;}
        public String getDirName() {return dirName+"_"+System.currentTimeMillis();}
        public boolean isEncryptMode() {return isEncryptMode;}
        public String getSuffix() {return fileSuffix;}
    }

    private Scanner sc;
    private FileSystem fileSystem;
    private MODE mode;
    private Encrypt.Transformation transformation;
    private String key;

    public EncryptMenu() {
        sc = new Scanner(System.in);
    }

    public void run() {
        while (true) {
            System.out.println("----------------------");
            if (!selectModeMenu()) return;
            this.fileSystem = UserInput.getFileSystemFromUser();
            if (!selectAlogrithmMenu()) return;
            if (!enterSecretKeyMenu()) return;
            
            Timer timer = new Timer();
            timer.start();
            FileSystem[] files = getFiles();
            processFiles(files);
            timer.stop();

            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append("----------------------\n");
            sBuilder.append("Done!\n");
            sBuilder.append("Mode: ").append(mode.getMode()).append("\n");
            sBuilder.append("Algorithm: ").append(transformation.getAlgorithm()).append("\n");
            sBuilder.append("Finished in: ").append(timer.getMeasuredTime()).append("\n");
            
            System.out.println(sBuilder.toString());
        }
    }

    private boolean selectModeMenu() {
        // print avaliable modes
        MODE[] modes = MODE.values();
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0; i < modes.length; i++) sBuilder.append(i+1).append(". ").append(modes[i].mode).append("\n");
        int backMenuNum = modes.length+1;
        sBuilder.append(backMenuNum).append(". Back to Main Menu\n");
        sBuilder.append("----------------------");

        System.out.println(sBuilder.toString());
        System.out.print("Enter your choice: ");
        
        int modeNum = UserInput.getNumberFromUser(1, backMenuNum);
        if (modeNum == backMenuNum) return false;
        mode = modes[modeNum-1];
        return true;
    }

    private boolean selectAlogrithmMenu() {
        System.out.println();

        // get avaliable encryption algorithms
        Encrypt.Transformation[] transformations = Encrypt.Transformation.values();

        // print message with avaliable encryption algorithms
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("Choose an Algorithms:\n");
        for (int i = 0; i < transformations.length; i++)
            sBuilder.append(i + 1).append(".").append(" ").append(transformations[i].getAlgorithm()).append("\n");
        sBuilder.append("----------------------");
        
        System.out.println(sBuilder.toString());
        System.out.print("Enter your choice: ");

        // get a valid number from the user
        int algorithmNum = UserInput.getNumberFromUser(1, transformations.length);
        transformation = transformations[algorithmNum-1];
        
        return true;
    }

    private boolean enterSecretKeyMenu() {
        System.out.println();

        int keySize = transformation.getKeySizeInBytes();
        while (true) {
            System.out.print("Enter the Secret Key ("+keySize+" byte(s)/Latin Character(s)): ");
            String key = sc.nextLine();
            if (key.getBytes().length != keySize) {
                System.out.println("Invalid Key, provided key length: "+key.getBytes().length+" byte(s)");
                continue;
            }
            this.key = key;
            return true;
        }
    }

    private Encrypt buildEncrypt() {
        Encrypt encrypt = new Encrypt.Builder()
            .setAlgorithm(transformation)
            .setEncryptionMode(mode.isEncryptMode())
            .setKey(key)
        .build();

        return encrypt;
    }

    private FileSystem[] getFiles() {
        FileSystem[] files = fileSystem.getFiles(null, true);
        if (files == null) files = new FileSystem[] { fileSystem };
        return files;
    }

    private void processFiles(FileSystem[] files) {
        String dirName = mode.getDirName();

        Parallel.runParallel(files.length, (ParallelParameters parallelParameters) -> {
            Encrypt encrypt = buildEncrypt();

            int my_rank = parallelParameters.my_rank;
            int my_start = parallelParameters.my_start;
            int my_last = parallelParameters.my_last;
            for (int i = my_start; i < my_last; i++) {
                FileSystem file = files[i];

                try {
                    FileSystem outputDir = file.createDirectory(dirName);
                    FileSystem outputFile = outputDir.createFile(getNewFileName(file.getFile().getName()));
    
                    file.read((fileBytes, isFinal) -> {
                        encrypt.setText(fileBytes);
                        try {
                            outputFile.append(encrypt.update(isFinal));
                            if (isFinal) {
                                System.out.println("* Thread " + my_rank + ": " + file.getFile().getName() + " -> " + outputFile.getFile().getParentFile().getName() + File.separator + outputFile.getFile().getName());
                                // System.gc(); // run java garbage collector
                            }
                        } catch (IllegalBlockSizeException | BadPaddingException e) {
                            System.out.println("Encryption/Decryption Failed: " + e.getMessage());
                            return;
                        } catch (IOException e) {
                            System.out.println("I/O Error");
                            e.printStackTrace();
                            return;
                        }
                    });
    
                }
                catch (FileNotFoundException e) { System.out.println(file.getFile().getAbsolutePath() + " is Not Found"); } 
                catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    private String getNewFileName(String fileName) {
        if (!mode.isEncryptMode && fileName.endsWith(mode.getSuffix())) 
            fileName = fileName.substring(0, fileName.length() - mode.getSuffix().length()); // remove suffix from fileName
        else if (mode.isEncryptMode) fileName += mode.getSuffix();
        return fileName;
    }
}