package me.videogamesm12.rgitgrab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.Getter;
import me.videogamesm12.rgitgrab.data.RBXBranch;
import me.videogamesm12.rgitgrab.data.RBXVersion;
import me.videogamesm12.rgitgrab.data.RGGConfiguration;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Main
{
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger logger = LoggerFactory.getLogger("RGitGrab");
    private static final DateFormat responseDateFormat = new SimpleDateFormat("'['EEE',' dd MMM yyyy HH':'mm':'ss zzz']'");
    @Getter
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static RGGConfiguration configuration = null;

    public static void main(String[] args) throws GitAPIException
    {
        // Read the parameters
        configuration = RGGConfiguration.getFromParameters(args);

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
        final List<RevCommit> commits = StreamSupport.stream(git.log().call().spliterator(), false).sorted(Comparator.comparingInt(RevCommit::getCommitTime)).toList();
        final Pattern pattern = Pattern.compile("([A-z0-9]+): (version-[A-z0-9]+) \\[([0-9.]+)]");
        final AtomicInteger unique = new AtomicInteger();

        logger.info("Now it's time to get funky");
        commits.forEach(commit ->
        {
            logger.info("Reading commit {}", commit.getName());

            try
            {
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commit.getName()).call();
            }
            catch (GitAPIException ex)
            {
                logger.error("Failed to revert repository to commit {}", commit.getName(), ex);
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
                        final List<RBXVersion> type = switch (matcher.group(1).toLowerCase())
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

                        if (type.stream().noneMatch(version -> version.equals(matcher.group(2))))
                        {
                            logger.info("Adding new version {} of type {}", matcher.group(2), matcher.group(1));
                            String[] version = matcher.group(3).split("\\.");
                            type.add(new RBXVersion(matcher.group(2), RBXVersion.Type.findType(matcher.group(1).toLowerCase()),
                                    fetchVersionDate(branchName, matcher.group(2), commit, !configuration.isSpeed()), version[0], version[1], version[2], version[3]));
                            unique.incrementAndGet();
                        }
                    }
                }
                catch (InterruptedException ex)
                {
                    logger.error("Unable to read timestamp response from server", ex);
                }
                catch (ParseException ex)
                {
                    logger.error("Unable to parse timestamp response from server", ex);
                }
                catch (IOException ex)
                {
                    logger.error("Failed to read information for branch {}", branchName, ex);
                }
            });
        });

        logger.info("Found {} unique clients in {} branches", unique.get(), branches.size());

        dumpResults(branches);

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

    private static void dumpResults(Map<String, RBXBranch> branches)
    {
        switch (configuration.getDestination())
        {
            case ARIA2C ->
            {
                final ThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);
                final ThreadPoolExecutor sender = new ScheduledThreadPoolExecutor(8);
                final Pattern filePattern = Pattern.compile("^([A-z0-9-]+\\.[A-z0-9]+)");
                branches.forEach((name, branch) ->
                {
                    executor.execute(() ->
                    {
                        // TODO: This could be executed better but it'll be fine for now
                        branch.getStandardWindowsVersions().forEach(client ->
                        {
                            final List<String> files = new ArrayList<>();

                            logger.info("Preparing to send version {}", client.getHash());
                            try
                            {
                                final HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                                        .uri(URI.create("https://s3.amazonaws.com/setup.roblox.com/" +
                                                (!name.equalsIgnoreCase( "live") ? "channel/" + name + "/" : "")
                                                + client.getHash() + "-rbxPkgManifest.txt"))
                                        .build(), HttpResponse.BodyHandlers.ofString());

                                Arrays.stream(response.body().split("\r\n")).forEach(line ->
                                {
                                    if (filePattern.matcher(line).find())
                                    {
                                        files.add(client.getHash() + "-" + line);
                                    }
                                });
                            }
                            catch (InterruptedException ex)
                            {
                                return;
                            }
                            catch (IOException ex)
                            {
                                logger.error("An error occurred while attempting to get the manifest for version {}", client.getHash(), ex);
                                return;
                            }

                            logger.info("Queuing files for version {}", client.getHash());
                            files.forEach(url ->
                            {
                                sender.submit(() ->
                                {
                                    try
                                    {
                                        httpClient.send(createRequestForFile(name, client,"", url), HttpResponse.BodyHandlers.ofString());
                                    }
                                    catch (Exception ex)
                                    {
                                        logger.error("Failed to send request to aria2c instance", ex);
                                    }
                                });
                            });
                        });

                        branch.getCJVWindowsVersions().forEach(client ->
                        {
                            final List<String> files = new ArrayList<>();

                            logger.info("Preparing to send version {}", client.getHash());
                            try
                            {
                                final HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                                        .uri(URI.create("https://s3.amazonaws.com/setup.roblox.com/" +
                                                (!name.equalsIgnoreCase( "live") ? "channel/" + name + "/" : "")
                                                + "cjv/" + client.getHash() + "-rbxPkgManifest.txt"))
                                        .build(), HttpResponse.BodyHandlers.ofString());

                                Arrays.stream(response.body().split("\r\n")).forEach(line ->
                                {
                                    if (filePattern.matcher(line).find())
                                    {
                                        files.add(client.getHash() + "-" + line);
                                    }
                                });
                            }
                            catch (InterruptedException ex)
                            {
                                return;
                            }
                            catch (IOException ex)
                            {
                                logger.error("An error occurred while attempting to get the manifest for version {}", client.getHash(), ex);
                                return;
                            }

                            logger.info("Queuing files for version {}", client.getHash());
                            files.forEach(url ->
                            {
                                sender.submit(() ->
                                {
                                    try
                                    {
                                        httpClient.send(createRequestForFile(name, client, "cjv/", url), HttpResponse.BodyHandlers.ofString());
                                    }
                                    catch (Exception ex)
                                    {
                                        logger.error("Failed to send request to aria2c instance", ex);
                                    }
                                });
                            });
                        });

                        branch.getStandardMacVersions().forEach(client ->
                        {
                            final List<String> files = new ArrayList<>();

                            logger.info("Preparing to send version {}", client.getHash());
                            if (client.getType() == RBXVersion.Type.MAC_PLAYER)
                            {
                                files.add(client.getHash() + "-Roblox.dmg");
                                files.add(client.getHash() + "-RobloxPlayer.zip");
                            }
                            else
                            {
                                files.add(client.getHash() + "-RobloxStudioApp.zip");
                                files.add(client.getHash() + "-RobloxStudio.zip");
                                files.add(client.getHash() + "-RobloxStudio.dmg");
                            }

                            logger.info("Queuing files for version {}", client.getHash());
                            files.forEach(url ->
                            {
                                sender.submit(() ->
                                {
                                    try
                                    {
                                        httpClient.send(createRequestForFile(name, client, "mac/", url), HttpResponse.BodyHandlers.ofString());
                                    }
                                    catch (Exception ex)
                                    {
                                        logger.error("Failed to send request to aria2c instance", ex);
                                    }
                                });
                            });
                        });

                        branch.getCJVMacVersions().forEach(client ->
                        {
                            final List<String> files = new ArrayList<>();

                            logger.info("Preparing to send version {}", client.getHash());
                            if (client.getType() == RBXVersion.Type.MAC_PLAYER)
                            {
                                files.add(client.getHash() + "-Roblox.dmg");
                                files.add(client.getHash() + "-RobloxPlayer.zip");
                            }
                            else
                            {
                                files.add(client.getHash() + "-RobloxStudioApp.zip");
                                files.add(client.getHash() + "-RobloxStudio.zip");
                                files.add(client.getHash() + "-RobloxStudio.dmg");
                            }

                            logger.info("Queuing files for version {}", client.getHash());
                            files.forEach(url ->
                            {
                                sender.submit(() ->
                                {
                                    try
                                    {
                                        httpClient.send(createRequestForFile(name, client, "mac/cjv/", url), HttpResponse.BodyHandlers.ofString());
                                    }
                                    catch (Exception ex)
                                    {
                                        logger.error("Failed to send request to aria2c instance", ex);
                                    }
                                });
                            });
                        });
                    });
                });
            }
            case DEPLOY_HISTORY ->
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
                        Stream.of(branch.getMacStudio(), branch.getMacPlayer()).flatMap(Collection::stream)
                                .sorted(Comparator.comparingLong(RBXVersion::getDate)).forEach(client -> mac.append(client.toDeployString()));

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
                        Stream.of(branch.getMacStudioCJV()).flatMap(Collection::stream)
                                .sorted(Comparator.comparingLong(RBXVersion::getDate)).forEach(client -> macCJV.append(client.toDeployString()));

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
                    if (!branch.getWindowsStudio().isEmpty() || !branch.getWindowsStudio64().isEmpty() || !branch.getWindowsPlayer().isEmpty())
                    {
                        final StringBuilder win = new StringBuilder();
                        Stream.of(branch.getWindowsStudio(), branch.getWindowsStudio64(), branch.getWindowsPlayer()).flatMap(Collection::stream)
                                .sorted(Comparator.comparingLong(RBXVersion::getDate)).forEach(client -> win.append(client.toDeployString()));

                        try
                        {
                            FileUtils.writeStringToFile(new File(file, "DeployHistory." + name + ".txt"), win.toString(), Charset.defaultCharset());
                        }
                        catch (IOException ex)
                        {
                            logger.error("Failed to write DeployHistory-formatted file on branch {}", name, ex);
                        }
                    }

                    // CJV
                    if (!branch.getWindowsStudioCJV().isEmpty() || !branch.getWindowsStudio64CJV().isEmpty())
                    {
                        final StringBuilder winCJV = new StringBuilder();
                        Stream.of(branch.getWindowsStudioCJV(), branch.getWindowsStudio64CJV()).flatMap(Collection::stream)
                                .sorted(Comparator.comparingLong(RBXVersion::getDate)).forEach(client -> winCJV.append(client.toDeployString()));

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
            case JSON ->
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

    public static long fetchVersionDate(String channel, String hash, RevCommit commit, boolean pullOnline) throws ParseException, IOException, InterruptedException
    {
        // If pullOnline is set to true, then we use the much more accurate but incredibly slow method of retrieving the
        //  date using the Roblox setup bucket
        if (pullOnline)
        {
            final HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://s3.amazonaws.com/setup.roblox.com/" +
                            (!channel.equalsIgnoreCase( "live") ? "channel/" + channel + "/" : "")
                            + hash + "-RobloxVersion.txt"))
                    .build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 403)
            {
                return responseDateFormat.parse(response.headers().map().get("Last-Modified").toString()).getTime();
            }
        }

        return commit.getCommitTime() * 1000L;
    }

    public static HttpRequest createRequestForFile(String channel, RBXVersion version, String suffix, String file)
    {
        // Create an aria2c request
        final JsonObject object = new JsonObject();
        object.addProperty("jsonrpc", "2.0");
        object.addProperty("method", "aria2.addUri");

        final JsonArray params = new JsonArray();
        params.add("token:");
        final JsonArray link = new JsonArray();
        link.add("https://s3.amazonaws.com/setup.roblox.com/" +
                (!channel.equalsIgnoreCase("live") ? "channel/" + channel + "/" : "")
                + suffix + file);
        params.add(link);
        final JsonObject output = new JsonObject();
        output.addProperty("out", (!channel.equalsIgnoreCase("live") ? channel + "/" + suffix : "")
                + version.getHash() + "/"
                + file);
        params.add(output);
        object.add("params", params);
        object.addProperty("id", Instant.now().toEpochMilli());

        return HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:6800/jsonrpc"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(object)))
                .build();
    }
}