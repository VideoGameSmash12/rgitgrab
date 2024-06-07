package me.videogamesm12.rgitgrab.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RequiredArgsConstructor
@Getter
public class RBXVersion
{
    private static final DateFormat dateFormat = new SimpleDateFormat("M'/'d'/'yyyy h':'mm':'ss a");
    //--
    private final String hash;
    private final Type type;
    private final long date;
    //--
    private final String superMajor;
    private final String major;
    private final String minor;
    private final String superMinor;

    public boolean equals(String hash)
    {
        return this.hash.equalsIgnoreCase(hash);
    }

    public String getVersion()
    {
        return String.format("%s, %s, %s, %s", superMajor, major, minor, superMinor);
    }

    public String toDeployString()
    {
        return String.format("New %s %s at %s, file version: %s....Done!\n", type.getDeployName(), getHash(), dateFormat.format(new Date(getDate())), getVersion());
    }

    @Getter
    public enum Type
    {
        MAC_STUDIO("Studio", "MacStudio", "MacStudioCJV"),
        MAC_PLAYER("Client", "MacPlayer"),
        WINDOWS_PLAYER("WindowsPlayer", "WindowsPlayer"),
        WINDOWS_STUDIO("Studio", "WindowsStudio", "WindowsStudioCJV"),
        WINDOWS_STUDIO_64("Studio64", "WindowsStudio64", "WindowsStudio64CJV");

        private final String deployName;
        private final List<String> acceptedTypes;

        Type(String deployName, String... acceptedTypes)
        {
            this.deployName = deployName;
            this.acceptedTypes = Arrays.asList(acceptedTypes);
        }

        public static Type findType(String name)
        {
            return Arrays.stream(values()).filter(type -> type.getAcceptedTypes().stream().anyMatch(typeName ->
                    typeName.equalsIgnoreCase(name))).findAny().orElse(null);
        }
    }
}
