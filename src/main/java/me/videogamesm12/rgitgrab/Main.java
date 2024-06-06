package me.videogamesm12.rgitgrab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.videogamesm12.rgitgrab.data.RBXBranch;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public class Main
{
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger logger = LoggerFactory.getLogger("RGitGrab");

    public static void main(String[] args) throws GitAPIException
    {
        // Set up our working directory
        final File folder = new File("temp");
        if (folder.exists() && folder.isDirectory())
        {
            logger.warn("Working folder already exists, deleting");
            try
            {
                FileUtils.deleteDirectory(folder);
            }
            catch (IOException ex)
            {
                logger.error("Failed to delete existing working folder", ex);
                return;
            }
        }

        logger.info("Cloning repository...");
        final Git git = Git.cloneRepository()
                .setURI("https://github.com/bluepilledgreat/Roblox-DeployHistory-Tracker.git")
                .setDirectory(folder)
                .call();

        final Map<String, RBXBranch> branches = new HashMap<>();
        final List<String> commits = StreamSupport.stream(git.log().call().spliterator(), false)/*.sorted(Comparator.comparingInt(RevCommit::getCommitTime))*/.map(AnyObjectId::getName).toList();
        final Pattern pattern = Pattern.compile("([A-z0-9]+): (version-[A-z0-9]+) \\[([0-9.]+)]");
        final AtomicInteger unique = new AtomicInteger();

        logger.info("Now it's time to get funky");
        commits.forEach(commit ->
        {
            logger.info("Reading commit {}", commit);

            try
            {
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commit).call();
            }
            catch (GitAPIException ex)
            {
                logger.error("Failed to revert repository to commit {}", commit, ex);
                return;
            }

            Arrays.stream(Objects.requireNonNull(folder.listFiles())).filter(file -> file.getName().endsWith(".txt")).toList().forEach(file ->
            {
                final String branchName = file.getName().toLowerCase().replace(".txt", "");
                logger.info("Found file for branch {}", branchName);

                if (!branches.containsKey(branchName))
                {
                    logger.info("Creating branch container for new branch {}", branchName);
                    branches.put(branchName, new RBXBranch());
                }

                final RBXBranch branch = branches.get(branchName);

                try
                {
                    final Scanner scanner = new Scanner(file);

                    while (scanner.hasNext())
                    {
                        String line = scanner.nextLine();
                        Matcher matcher = pattern.matcher(line);
                        if (!matcher.find())
                        {
                            continue;
                        }

                        // Determine what type of client this line is
                        final List<String> type = switch (matcher.group(1).toLowerCase())
                        {
                            case "windowsplayer" -> branch.getWindowsPlayer();
                            case "windowsstudio" -> branch.getWindowsStudio();
                            case "windowsstudiocjv" -> branch.getWindowsStudioCJV();
                            case "windowsstudio64" -> branch.getWindowsStudio64();
                            case "windowsstudio64cjv" -> branch.getWindowsStudio64CJV();
                            case "macplayer" -> branch.getMacPlayer();
                            case "macstudio" -> branch.getMacStudio();
                            case "macstudiocjv" -> branch.getMacStudioCJV();
                            default -> throw new RuntimeException("Unknown client type " + matcher.group(1).toLowerCase());
                        };

                        if (!type.contains(matcher.group(2)))
                        {
                            logger.info("Adding new version {} of type {}", matcher.group(2), matcher.group(1));
                            type.add(matcher.group(2));
                            unique.incrementAndGet();
                        }
                    }
                }
                catch (IOException ex)
                {
                    logger.error("Failed to read information for branch {}", branchName, ex);
                }
            });
        });

        logger.info("Found {} unique clients in {} branches", unique.get(), branches.size());

        dumpResults(branches, true);

        logger.info("Cleaning up");
        try
        {
            FileUtils.deleteDirectory(folder);
        }
        catch (IOException ex)
        {
            logger.warn("Failed to delete existing working folder", ex);
            return;
        }

        logger.info("Done!");
    }

    private static void dumpResults(Map<String, RBXBranch> branches, boolean deployFormat)
    {
        logger.info("Dumping results to disk");

        if (deployFormat)
        {
            final File file = new File("deploys");
            file.mkdirs();

            branches.forEach((name, branch) ->
            {
                //-- Mac --//
                if (!branch.getMacStudio().isEmpty() || !branch.getMacPlayer().isEmpty())
                {
                    // Standard
                    final StringBuilder mac = new StringBuilder();
                    branch.getMacStudio().forEach(studio -> mac.append(String.format("New Studio %s at 4/20/2069 12:00:00 AM, file version: 0, 0, 0, 0....Done!\n", studio)));
                    branch.getMacPlayer().forEach(player -> mac.append(String.format("New Client %s at 4/20/2069 12:00:00 AM, file version: 0, 0, 0, 0....Done!\n", player)));

                    try
                    {
                        FileUtils.writeStringToFile(new File(file, "DeployHistory." + name + ".mac.txt"), mac.toString(), Charset.defaultCharset());
                    }
                    catch (IOException ex)
                    {
                        logger.error("Failed to write DeployHistory-formatted file for Mac on branch {}", name, ex);
                    }
                }

                // CJV
                if (!branch.getMacStudioCJV().isEmpty())
                {
                    final StringBuilder macCJV = new StringBuilder();
                    branch.getMacStudioCJV().forEach(studio -> macCJV.append(String.format("New Studio %s at 4/20/2069 12:00:00 AM, file version: 0, 0, 0, 0....Done!\n", studio)));

                    try
                    {
                        FileUtils.writeStringToFile(new File(file, "DeployHistory." + name + ".mac.cjv.txt"), macCJV.toString(), Charset.defaultCharset());
                    }
                    catch (IOException ex)
                    {
                        logger.error("Failed to write DeployHistory-formatted file for Mac CJV on branch {}", name, ex);
                    }
                }

                //-- Windows --//
                // Standard
                final StringBuilder win = new StringBuilder();
                branch.getWindowsStudio().forEach(studio -> win.append(String.format("New Studio %s at 4/20/2069 12:00:00 AM, file version: 0, 0, 0, 0....Done!\n", studio)));
                branch.getWindowsStudio64().forEach(studio -> win.append(String.format("New Studio64 %s at 4/20/2069 12:00:00 AM, file version: 0, 0, 0, 0....Done!\n", studio)));
                branch.getWindowsPlayer().forEach(player -> win.append(String.format("New WindowsPlayer %s at 4/20/2069 12:00:00 AM, file version: 0, 0, 0, 0....Done!\n", player)));

                try
                {
                    FileUtils.writeStringToFile(new File(file, "DeployHistory." + name + ".txt"), win.toString(), Charset.defaultCharset());
                }
                catch (IOException ex)
                {
                    logger.error("Failed to write DeployHistory-formatted file on branch {}", name, ex);
                }

                // CJV
                if (!branch.getWindowsStudioCJV().isEmpty() || !branch.getWindowsStudio64CJV().isEmpty())
                {
                    final StringBuilder winCJV = new StringBuilder();
                    branch.getWindowsStudioCJV().forEach(studio -> winCJV.append(String.format("New Studio %s at 4/20/2069 12:00:00 AM, file version: 0, 0, 0, 0....Done!\n", studio)));
                    branch.getWindowsStudio64CJV().forEach(studio -> winCJV.append(String.format("New Studio64 %s at 4/20/2069 12:00:00 AM, file version: 0, 0, 0, 0....Done!\n", studio)));

                    try
                    {
                        FileUtils.writeStringToFile(new File(file, "DeployHistory." + name + ".cjv.txt"), winCJV.toString(), Charset.defaultCharset());
                    }
                    catch (IOException ex)
                    {
                        logger.error("Failed to write DeployHistory-formatted file for CJV on branch {}", name, ex);
                    }
                }
            });
        }
        else
        {
            try
            {
                BufferedWriter writer = Files.newBufferedWriter(new File("versions.json").toPath());
                gson.toJson(branches, writer);
                writer.close();
            }
            catch (Exception ex)
            {
                logger.error("Failed to dump branch data to disk", ex);
                logger.debug(branches.toString());
            }
        }
    }
}