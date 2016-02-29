/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iii.vop2016.verkeer2.ejb.timer;

import com.sun.faces.facelets.util.DevTools;
import iii.vop2016.verkeer2.ejb.datamanager.ITrafficDataManager;
import iii.vop2016.verkeer2.ejb.datamanager.TrafficDataManagerRemote;
import iii.vop2016.verkeer2.ejb.helper.BeanFactory;
import iii.vop2016.verkeer2.ejb.helper.HelperFunctions;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 *
 * @author Tobias Van der Pulst
 */
@Singleton
@Startup
public class TimerSchedular implements TimerSchedularRemote {

    @Resource
    protected SessionContext ctxs;

    protected Timer t;
    protected int ticks;
    protected int interval;
    protected SimpleDateFormat sdf = new SimpleDateFormat("HHmm");
    protected Pattern timeFormat = Pattern.compile("([0-9]{2})-([0-9]{2})");
    protected static final int DEFAULTINTERVAL = 5;

    protected Pattern numberFormat = Pattern.compile("\\d+");

    protected InitialContext ctx;
    protected static final String JNDILOOKUP_PROPERTYFILE = "resources/properties/TimerScheduler";
    protected Properties properties;
    
    protected BeanFactory beans;

    /**
     * Constructor
     */
    public TimerSchedular() {
    }

    @PostConstruct
    private void init() {
        //Initialize bean and its context
        try {
            ctx = new InitialContext();
        } catch (NamingException ex) {
            Logger.getLogger(TimerSchedular.class.getName()).log(Level.SEVERE, null, ex);
        }
        beans = BeanFactory.getInstance(ctx, ctxs);

        //Get properties file for bean
        properties = HelperFunctions.RetrievePropertyFile(JNDILOOKUP_PROPERTYFILE, ctx, Logger.getGlobal());

        //Get interval to closest time for timer from properties file
        int currentTime = getIndexedCurrentTime();
        interval = getIntervalForClosestTime(currentTime);
        Logger.getGlobal().log(Level.INFO, "Interval for Timer set to " + interval);
        
        

        //Create timer with specified interval
        ticks=0;
        t = ctxs.getTimerService().createIntervalTimer(1000, 60000, new TimerConfig());
    }

    /**
     * Ticks are driven by the Timers, they start the new download cycle for data
     */
    @Override
    @Timeout
    public void Tick() {
        int currentTime = getIndexedCurrentTime();
        
        if(ticks == interval){
            ticks = 1;
            DoTick();
        }else{
            ticks++;
        }

        //get interval for current time, if different set the timer with specified interval
        int i = getIntervalForClosestTime(currentTime);
        if (i != interval) {
            DoTick();
            
            interval = i;
            ticks = 1;
            Logger.getGlobal().log(Level.INFO, "Interval for Timer set to " + interval);
        }

    }

    private int getIndexedCurrentTime() {
        Calendar cal = Calendar.getInstance();
        int currentTime = Integer.parseInt(sdf.format(cal.getTime()));
        return currentTime;
    }

    private int getIntervalForClosestTime(int currentTime) {
        if (properties == null) {
            return DEFAULTINTERVAL;
        }

        int closestTime = Integer.MAX_VALUE;
        int interval = DEFAULTINTERVAL;

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            //check if key is string
            if ((entry.getKey() != null) && (entry.getKey() instanceof String)) {
                String key = (String) entry.getKey();
                //check if key matches format
                Matcher m = timeFormat.matcher(key);
                if (m.matches()) {
                    int time = Integer.parseInt(m.group(1) + m.group(2));
                    int diff = currentTime - time;
                    //only check when time is earlier then current time
                    if (diff >= 0) {
                        //if time is closer then prev time, get its interval
                        if (diff < closestTime) {
                            if ((entry.getValue() != null) && (entry.getValue() instanceof String) && (numberFormat.matcher((String) entry.getValue()).matches())) {
                                closestTime = diff;
                                interval = Integer.parseInt((String) entry.getValue());
                            } else {
                                Logger.getGlobal().log(Level.WARNING, "Config file error: <" + entry.getKey() + " - " + entry.getValue() + "> expected a valid object (int).");
                            }
                        }
                    }
                }
            }
        }

        return interval;
    }

    private void DoTick() {     
        //lookup datamanager bean and trigger timed function
        ITrafficDataManager managementBean = beans.getDataManager();
        if (managementBean != null) {
            System.out.println("Tick Tick It's me");
            managementBean.downloadNewData(new Date());
        } else {
            Logger.getGlobal().log(Level.SEVERE, "Could not access dataManagement bean to trigger Timed function");
        }

    }

}
