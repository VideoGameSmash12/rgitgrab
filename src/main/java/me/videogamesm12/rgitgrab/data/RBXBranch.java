package me.videogamesm12.rgitgrab.data;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

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
}
