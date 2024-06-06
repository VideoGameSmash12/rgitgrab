package me.videogamesm12.rgitgrab.data;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class RBXBranch
{
    private final List<String> windowsPlayer = new ArrayList<>();
    private final List<String> windowsStudio = new ArrayList<>();
    private final List<String> windowsStudioCJV = new ArrayList<>();
    private final List<String> windowsStudio64 = new ArrayList<>();
    private final List<String> windowsStudio64CJV = new ArrayList<>();
    private final List<String> macPlayer = new ArrayList<>();
    private final List<String> macStudio = new ArrayList<>();
    private final List<String> macStudioCJV = new ArrayList<>();
}
