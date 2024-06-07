package me.videogamesm12.rgitgrab.data;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Getter
public class RBXBranch
{
    private final List<RBXVersion> windowsPlayer = new ArrayList<>();
    private final List<RBXVersion> windowsStudio = new ArrayList<>();
    private final List<RBXVersion> windowsStudioCJV = new ArrayList<>();
    private final List<RBXVersion> windowsStudio64 = new ArrayList<>();
    private final List<RBXVersion> windowsStudio64CJV = new ArrayList<>();
    private final List<RBXVersion> macPlayer = new ArrayList<>();
    private final List<RBXVersion> macStudio = new ArrayList<>();
    private final List<RBXVersion> macStudioCJV = new ArrayList<>();

    public List<RBXVersion> getStandardMacVersions()
    {
        return Stream.of(getMacStudio(), getMacPlayer()).flatMap(Collection::stream)
                .sorted(Comparator.comparingLong(RBXVersion::getDate)).toList();
    }

    public List<RBXVersion> getCJVMacVersions()
    {
        return Stream.of(getMacStudioCJV()).flatMap(Collection::stream)
                .sorted(Comparator.comparingLong(RBXVersion::getDate)).toList();
    }


    public List<RBXVersion> getStandardWindowsVersions()
    {
        return Stream.of(getWindowsPlayer(), getWindowsStudio(), getWindowsStudio64()).flatMap(Collection::stream)
                .sorted(Comparator.comparingLong(RBXVersion::getDate)).toList();
    }

    public List<RBXVersion> getCJVWindowsVersions()
    {
        return Stream.of(getWindowsStudioCJV(), getWindowsStudio64CJV()).flatMap(Collection::stream)
                .sorted(Comparator.comparingLong(RBXVersion::getDate)).toList();
    }
}