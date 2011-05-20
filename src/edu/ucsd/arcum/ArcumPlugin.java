package edu.ucsd.arcum;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;


/**
 * The activator class controls the plug-in life cycle
 */
public class ArcumPlugin extends AbstractUIPlugin
{
    public static final boolean DEBUG = false;
    
    // The plug-in ID
    public static final String PLUGIN_ID = "edu.ucsd.arcum";
    public static final String NATURE_ID = PLUGIN_ID + ".arcumNature";
    public static final String BUILDER_ID = PLUGIN_ID + ".arcumBuilder";
    public static final String SOURCE_ID = PLUGIN_ID + ".arcumSource";
    public static final String MARKER_ID = PLUGIN_ID + ".arcumMarker";
    
    // The shared instance
    private static ArcumPlugin plugin;

    /**
     * The constructor
     */
    public ArcumPlugin() {
        plugin = this;
    }

    public void start(BundleContext context) throws Exception {
        super.start(context);
    }

    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance
     * 
     * @return the shared instance
     */
    public static ArcumPlugin getDefault() {
        return plugin;
    }
}