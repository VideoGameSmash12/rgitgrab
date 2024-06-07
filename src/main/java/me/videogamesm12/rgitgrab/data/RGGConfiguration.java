package me.videogamesm12.rgitgrab.data;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class RGGConfiguration
{
    /**
     * <p>Whether to dump the results of our digging into a neatly-organized JSON file</p>
     */
    private final DestinationType destination;

    /**
     * <p>Whether to prioritize speed when getting the metadata for clients. This comes at a cost of accuracy when
     * attempting to get the timestamp as for when a client was deployed.</p>
     */
    private final boolean speed;

    public static RGGConfiguration getFromParameters(String[] args)
    {
        final OptionParser parser = new OptionParser();
        parser.accepts("speed", "Prioritize speed when finding clients over accuracy");
        parser.accepts("destination", "What to do with the data we've scraped").withRequiredArg();

        final OptionSet options = parser.parse(args);
        return builder()
                .speed(options.has("speed"))
                .destination(options.has("destination") ? DestinationType.fromObject(options.valueOf("destination")) : DestinationType.DEPLOY_HISTORY)
                .build();
    }

    public enum DestinationType
    {
        /**
         * <p>Send everything we find to an aria2 daemon to deal with</p>
         */
        ARIA2C,
        /**
         * <p>Generate a DeployHistory-formatted text file for every channel</p>
         */
        DEPLOY_HISTORY,
        /**
         * <p>Generate a neatly organized JSON file listing all the channels and their particular version hashes.
         */
        JSON;

        public static DestinationType fromObject(Object obj)
        {
            if (obj instanceof Integer intObj)
            {
                return values()[Math.abs(intObj)];
            }
            else if (obj instanceof String strObj)
            {
                return valueOf(strObj.toUpperCase());
            }
            else
            {
                throw new IllegalArgumentException("Invalid object type");
            }
        }
    }
}
