/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iii.vop2016.verkeer2.ejb.dataprovider;

import iii.vop2016.verkeer2.ejb.components.IRoute;
import iii.vop2016.verkeer2.ejb.components.IRouteData;
import iii.vop2016.verkeer2.ejb.components.Weekdays;
import iii.vop2016.verkeer2.ejb.dao.Aggregation;
import iii.vop2016.verkeer2.ejb.dao.AggregationContainer;
import iii.vop2016.verkeer2.ejb.dao.ITrafficDataDAO;
import iii.vop2016.verkeer2.ejb.helper.BeanFactory;
import iii.vop2016.verkeer2.ejb.helper.HelperFunctions;
import iii.vop2016.verkeer2.ejb.properties.IProperties;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.AccessTimeout;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.SessionContext;
import javax.ejb.Singleton;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 *
 * @author Tobias
 */
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@Lock(LockType.READ)
@Singleton
public class DataProvider implements DataProviderRemote, DataProviderLocal {

    @Resource
    protected SessionContext ctxs;
    protected InitialContext ctx;
    protected BeanFactory beans;
    protected static final String JNDILOOKUP_PROPERTYFILE = "resources/properties/DataProvider";

    @PostConstruct
    private void init() {
        //Initialize bean and its context
        try {
            ctx = new InitialContext();
        } catch (NamingException ex) {
            Logger.getLogger(DataProvider.class.getName()).log(Level.SEVERE, null, ex);
        }
        beans = BeanFactory.getInstance(ctx, ctxs);

        IProperties propCol = beans.getPropertiesCollection();
        if (propCol != null) {
            propCol.registerProperty(JNDILOOKUP_PROPERTYFILE);
        }

        beans.getLogger().log(Level.INFO, "DataProvider has been initialized.");
    }

    private Properties getProperties() {
        return HelperFunctions.RetrievePropertyFile(JNDILOOKUP_PROPERTYFILE, ctx, Logger.getGlobal());
    }

    private int CalculateArithmaticMean(List<IRouteData> data, Function<IRouteData, Long> var, Function<IRouteData, Long> weight) {
        long totalNum = 0;
        long totalDiv = 0;
        int i = 0;
        for (IRouteData d : data) {
            long w = weight.apply(d);
            long v = var.apply(d);
            totalNum += w * v;
            totalDiv += w;
            i++;
        }

        if (totalDiv == 0) {
            return -1;
        }

        return Math.toIntExact(totalNum / totalDiv);
    }

    private static Function<IRouteData, Long> function_distance = new Function<IRouteData, Long>() {
        @Override
        public Long apply(IRouteData t) {
            return new Long(t.getDistance());
        }
    };

    private static Function<IRouteData, Long> function_distanceMulDuration = new Function<IRouteData, Long>() {
        @Override
        public Long apply(IRouteData t) {
            return new Long(t.getDistance() * t.getDuration());
        }
    };

    @Override

    public int getMeanDurationFromRouteData(List<IRouteData> routeData) {
        if (routeData == null || routeData.size() == 0) {
            return -1;
        }

        long routeId = routeData.get(0).getRouteId();

        for (IRouteData data : routeData) {
            if (data.getRouteId() != routeId) {
                return -1;
            }
        }

        return CalculateArithmaticMean(routeData, new Function<IRouteData, Long>() {
            @Override
            public Long apply(IRouteData t) {
                return new Long(t.getDuration());
            }
        }, function_distance);
    }

    private static Function<IRouteData, Long> function_distanceMulDistanceDivDuration = new Function<IRouteData, Long>() {
        @Override
        public Long apply(IRouteData t) {
            if (t.getDuration() == 0) {
                return 0L;
            }
            return new Long(t.getDistance() * t.getDistance() / t.getDuration());
        }
    };

    private <T> T getDataFromBuffer(Map<Long, Map<IRoute, T>> buffer, IRoute route, List<String> providers, long hash) {
        if (buffer.containsKey(hash)) {
            Map<IRoute, T> lowerBuffer = buffer.get(hash);
            if (lowerBuffer.containsKey(route)) {
                return lowerBuffer.get(route);
            }
        }
        return null;
    }

    private <T> void setDataInBuffer(T value, Map<Long, Map<IRoute, T>> buffer, IRoute route, List<String> providers, long hash, T err) {
        if (value != err) {
            if (buffer.containsKey(hash)) {
                buffer.get(hash).put(route, value);
            } else {
                Map<IRoute, T> m = new ConcurrentHashMap<>();
                m.put(route, value);
                buffer.put(hash, m);
            }
        }
    }

    private Map<Long, Map<IRoute, Integer>> currentDuration = new ConcurrentHashMap<>();

    @Override

    public int getCurrentDuration(IRoute route, List<String> providers) {
        ////ILogger logger = beans.getLogger();
        ////logger.entering("DataProvider", "getCurrentDuration", new Object[]{route, providers});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }
        long hash = calculateHash(providers);

        Integer buffer = getDataFromBuffer(currentDuration, route, providers, hash);
        if (buffer == null || buffer == -1) {
            ITrafficDataDAO dao = beans.getTrafficDataDAO();
            List<IRouteData> list = dao.getCurrentTrafficSituation(route, providers);

            if (list == null || list.isEmpty()) {
                ////logger.exiting("DataProvider", "getCurrentDuration", -1);
                return -1;
            }

            buffer = CalculateArithmaticMean(list, new Function<IRouteData, Long>() {
                @Override
                public Long apply(IRouteData t) {
                    return new Long(t.getDuration());
                }
            }, function_distance);

            setDataInBuffer(buffer, currentDuration, route, providers, hash, -1);
        }
        ////logger.exiting("DataProvider", "getCurrentDuration", buffer);
        return buffer;
    }

    private Map<Long, Map<IRoute, Integer>> currentSpeed = new ConcurrentHashMap<>();

    @Override

    public int getCurrentVelocity(IRoute route, List<String> providers) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getCurrentVelocity", new Object[]{route, providers});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Integer buffer = getDataFromBuffer(currentSpeed, route, providers, hash);

        if (buffer == null || buffer == -1) {
            ITrafficDataDAO dao = beans.getTrafficDataDAO();
            List<IRouteData> list = dao.getCurrentTrafficSituation(route, providers);

            if (list == null || list.size() == 0) {
                //logger.exiting("DataProvider", "getCurrentVelocity", -1);
                return -1;
            }

            buffer = CalculateArithmaticMean(list, new Function<IRouteData, Long>() {
                @Override
                public Long apply(IRouteData t) {
                    return (long) t.getDistance() / (long) t.getDuration();
                }
            }, function_distance);

            setDataInBuffer(buffer, currentSpeed, route, providers, hash, -1);
        }
        //logger.exiting("DataProvider", "getCurrentVelocity", buffer);
        return buffer;
    }

    private Map<Long, Map<IRoute, Integer>> optimalDuration = new ConcurrentHashMap<>();

    @Override

    public int getOptimalDuration(IRoute route, List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Integer buffer = getDataFromBuffer(optimalDuration, route, providers, hash);

        if (buffer == null || buffer == -1) {
            Properties properties = getProperties();
            long timeframe = Integer.parseInt(properties.getProperty("OptimalDurationTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getOptimalDuration(route, providers, startDate, endDate);
            setDataInBuffer(buffer, optimalDuration, route, providers, hash, -1);
        }
        return buffer;
    }

    private Map<Long, Map<IRoute, Integer>> optimalSpeed = new ConcurrentHashMap<>();

    @Override

    public int getOptimalVelocity(IRoute route, List<String> providers) {

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Integer buffer = getDataFromBuffer(optimalSpeed, route, providers, hash);

        if (buffer == null || buffer == -1) {
            Properties properties = getProperties();
            long timeframe = Integer.parseInt(properties.getProperty("OptimalDurationTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getOptimalVelocity(route, providers, startDate, endDate);
            setDataInBuffer(buffer, optimalSpeed, route, providers, hash, -1);
        }
        return buffer;
    }

    @Override

    public int getOptimalDuration(IRoute route, List<String> providers, Date startDate, Date endDate) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getOptimalDuration", new Object[]{route, providers, startDate, endDate});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        Properties properties = getProperties();
        Calendar startTime = GetTimeFromPropertyValue(properties.getProperty("OptimalDurationStartHour", "00-00"));
        Calendar endTime = GetTimeFromPropertyValue(properties.getProperty("OptimalDurationEndHour", "00-00"));

        List<Date> startList = GenerateListForHourBetweenDates(startTime, startDate, endDate);
        List<Date> endList = GenerateListForHourBetweenDates(endTime, startDate, endDate);

        ITrafficDataDAO dao = beans.getTrafficDataDAO();

        List<Long> list = dao.getAggregateData(route, providers, startList, endList, new AggregationContainer(Aggregation.sum, "duration * distance"), new AggregationContainer(Aggregation.sum, "distance"));

        if (list == null || list.size() == 0) {
            //logger.exiting("DataProvider", "getOptimalDuration", -1);
            return -1;
        }

        if (list.size() == 2 && list.get(1) != 0) {
            int ret = Math.toIntExact(list.get(0) / list.get(1));
            //logger.exiting("DataProvider", "getOptimalDuration", ret);
            return ret;
        }
        //logger.exiting("DataProvider", "getOptimalDuration", -1);
        return -1;
    }

    @Override

    public int getOptimalVelocity(IRoute route, List<String> providers, Date startDate, Date endDate) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getOptimalVelocity", new Object[]{route, providers, startDate, endDate});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        Properties properties = getProperties();
        Calendar startTime = GetTimeFromPropertyValue(properties.getProperty("OptimalDurationStartHour", "00-00"));
        Calendar endTime = GetTimeFromPropertyValue(properties.getProperty("OptimalDurationEndHour", "00-00"));

        List<Date> startList = GenerateListForHourBetweenDates(startTime, startDate, endDate);
        List<Date> endList = GenerateListForHourBetweenDates(endTime, startDate, endDate);

        ITrafficDataDAO dao = beans.getTrafficDataDAO();

        List<Long> list = dao.getAggregateData(route, providers, startList, endList, new AggregationContainer(Aggregation.sum, "distance * distance / duration "), new AggregationContainer(Aggregation.sum, "distance"));

        if (list == null || list.size() == 0) {
            //logger.exiting("DataProvider", "getOptimalVelocity", -1);
            return -1;
        }

        if (list.size() == 2 && list.get(1) != 0) {
            int ret = Math.toIntExact(list.get(0) / list.get(1));
            //logger.exiting("DataProvider", "getOptimalVelocity", ret);
            return ret;
        }
        //logger.exiting("DataProvider", "getOptimalVelocity", -1);
        return -1;
    }

    private Map<Long, Map<IRoute, Integer>> avgDuration = new ConcurrentHashMap<>();

    @Override

    public int getAvgDuration(IRoute route, List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Integer buffer = getDataFromBuffer(avgDuration, route, providers, hash);

        if (buffer == null || buffer == -1) {
            Properties properties = getProperties();
            long timeframe = Integer.parseInt(properties.getProperty("AvgDurationTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getAvgDuration(route, providers, startDate, endDate);
            setDataInBuffer(buffer, avgDuration, route, providers, hash, -1);
        }
        return buffer;
    }

    private Map<Long, Map<IRoute, Integer>> avgSpeed = new ConcurrentHashMap<>();

    @Override

    public int getAvgVelocity(IRoute route, List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Integer buffer = getDataFromBuffer(avgSpeed, route, providers, hash);

        if (buffer == null || buffer == -1) {
            Properties properties = getProperties();
            long timeframe = Integer.parseInt(properties.getProperty("AvgDurationTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getAvgVelocity(route, providers, startDate, endDate);
            setDataInBuffer(buffer, avgSpeed, route, providers, hash, -1);
        }
        return buffer;
    }

    @Override

    public int getAvgDuration(IRoute route, List<String> providers, Date start, Date end) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getAvgDuration", new Object[]{route, providers, start, end});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        ITrafficDataDAO dao = beans.getTrafficDataDAO();

        List<Long> list = dao.getAggregateData(route, providers, start, end, new AggregationContainer(Aggregation.sum, "duration * distance"), new AggregationContainer(Aggregation.sum, "distance"));

        if (list == null || list.size() == 0) {
            //logger.exiting("DataProvider", "getAvgDuration", -1);
            return -1;
        }

        if (list.size() == 2 && list.get(1) != 0) {
            int ret = Math.toIntExact(list.get(0) / list.get(1));
            //logger.exiting("DataProvider", "getAvgDuration", ret);
            return ret;
        }
        //logger.exiting("DataProvider", "getAvgDuration", -1);
        return -1;
    }

    @Override

    public int getAvgVelocity(IRoute route, List<String> providers, Date start, Date end) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getAvgDuration", new Object[]{route, providers, start, end});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        ITrafficDataDAO dao = beans.getTrafficDataDAO();

        List<Long> list = dao.getAggregateData(route, providers, start, end, new AggregationContainer(Aggregation.sum, "distance * distance / duration "), new AggregationContainer(Aggregation.sum, "distance"));

        if (list == null || list.size() == 0) {
            //logger.exiting("DataProvider", "getAvgVelocity", -1);
            return -1;
        }

        if (list.size() == 2 && list.get(1) != 0) {
            int ret = Math.toIntExact(list.get(0) / list.get(1));
            //logger.exiting("DataProvider", "getAvgVelocity", ret);
            return ret;

        }
        //logger.exiting("DataProvider", "getAvgVelocity", -1);
        return -1;
    }

    @Override

    public int getCurrentDelayLevel(IRoute route, List<String> providers) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getCurrentDelayLevel", new Object[]{route, providers});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        int opt = this.getOptimalDuration(route, providers);

        if (opt == -1) {
            //logger.exiting("DataProvider", "getCurrentDelayLevel", -1);
            return -1;
        }

        int current = getCurrentDuration(route, providers);

        int ret = beans.getThresholdManager().getThresholdLevel(route, current - opt);
        //logger.exiting("DataProvider", "getCurrentDelayLevel", ret);
        return ret;
    }

    @Override

    public int getDelayLevel(IRoute route, List<String> providers, Date start, Date end) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getDelayLevel", new Object[]{route, providers});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        int opt = this.getOptimalDuration(route, providers);
        if (opt == -1) {
            //logger.exiting("DataProvider", "getDelayLevel", -1);
            return -1;
        }

        int pastAvg = getAvgDuration(route, providers, start, end);
        if (opt == -1) {
            //logger.exiting("DataProvider", "getDelayLevel", -1);
            return -1;
        }

        int ret = beans.getThresholdManager().getThresholdLevel(route, pastAvg - opt);
        //logger.exiting("DataProvider", "getDelayLevel", ret);
        return ret;
    }

    @Override

    public int getAvgDelayLevel(IRoute route, List<String> providers) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getAvgDelayLevel", new Object[]{route, providers});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        int opt = this.getOptimalDuration(route, providers);
        if (opt == -1) {
            //logger.exiting("DataProvider", "getAvgDelayLevel", -1);
            return -1;
        }

        int avg = getAvgDuration(route, providers);
        if (opt == -1) {
            //logger.exiting("DataProvider", "getAvgDelayLevel", -1);
            return -1;
        }
        int ret = beans.getThresholdManager().getThresholdLevel(route, avg - opt);
        //logger.exiting("DataProvider", "getAvgDelayLevel", ret);
        return ret;
    }

    private Map<Long, Map<IRoute, Integer>> distance = new ConcurrentHashMap<>();

    @Override

    public int getDistance(IRoute route, List<String> providers) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getDistance", new Object[]{route, providers});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Integer buffer = getDataFromBuffer(distance, route, providers, hash);

        if (buffer == null || buffer == -1) {
            ITrafficDataDAO dao = beans.getTrafficDataDAO();
            List<IRouteData> list = dao.getCurrentTrafficSituation(route, providers);

            long distance = 0;

            int i = 0;
            for (IRouteData r : list) {
                distance += r.getDistance();
                i++;
            }

            if (i == 0) {
                //logger.exiting("DataProvider", "getDistance", -1);
                return -1;
            }

            buffer = Math.toIntExact(distance / i);
            setDataInBuffer(buffer, this.distance, route, providers, hash, -1);
        }
        //logger.exiting("DataProvider", "getDistance", buffer);
        return buffer;
    }

    private Map<Long, Map<IRoute, Integer>> trend = new ConcurrentHashMap<>();

    @Override

    public int getTrend(IRoute route, List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Integer buffer = getDataFromBuffer(trend, route, providers, hash);

        if (buffer == null || buffer == -1) {
            Properties properties = getProperties();
            long timeframe = Integer.parseInt(properties.getProperty("TrendTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getTrend(route, providers, startDate, endDate);
            setDataInBuffer(buffer, trend, route, providers, hash, 0);
        }
        return buffer;
    }

    @Override

    public int getTrend(IRoute route, List<String> providers, Date start, Date end) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getTrend", new Object[]{route, providers, start, end});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        ITrafficDataDAO dao = beans.getTrafficDataDAO();
        List<IRouteData> data = dao.getData(route, start, end, providers);

        Map<Date, List<IRouteData>> sortedData = new HashMap<>();

        if (data == null || data.isEmpty()) {
            //logger.exiting("DataProvider", "getTrend", 0);
            return 0;
        }

        for (IRouteData d : data) {
            if (sortedData.containsKey(d.getTimestamp())) {
                sortedData.get(d.getTimestamp()).add(d);
            } else {
                List<IRouteData> arr = new ArrayList<>();
                arr.add(d);
                sortedData.put(d.getTimestamp(), arr);
            }
        }

        Map<Date, Integer> aggrData = new TreeMap<>();

        for (Map.Entry<Date, List<IRouteData>> entry : sortedData.entrySet()) {
            aggrData.put(entry.getKey(), getMeanDurationFromRouteData(entry.getValue()));
        }

        List<Integer> delta = new ArrayList<>();
        int prev = -1;
        for (Map.Entry<Date, Integer> entry : aggrData.entrySet()) {
            if (prev == -1) {
                prev = entry.getValue();
            } else {
                delta.add(entry.getValue() - prev);
                prev = entry.getValue();
            }
        }

        if (delta.size() < 2) {
            //logger.exiting("DataProvider", "getDistance", 0);
            return 0;
        }

        int trend = Integer.MIN_VALUE;

        for (Integer i : delta) {
            if (trend == Integer.MIN_VALUE) {
                trend = i;
            } else if ((trend <= 0 && i <= 0) || (trend >= 0 && i >= 0)) {
                int ie = Math.abs(i);
                int trende = Math.abs(trend);

                if (trende <= ie) {
                    trend = (i + trend) / 2;
                } else {
                    //trend indicates stablizing
                    //logger.exiting("DataProvider", "getTrend", 0);
                    return 0;
                }

            } else {
                //no trend, deltas change signs
                //logger.exiting("DataProvider", "getTrend", 0);
                return 0;
            }
        }
        //logger.exiting("DataProvider", "getTrend", trend);
        return trend;
    }

    private Map<Long, Map<IRoute, Map<Date, Integer>>> recentData = new ConcurrentHashMap<>();

    @Override

    public Map<Date, Integer> getRecentData(IRoute route, List<String> providers) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getRecentData", new Object[]{route, providers});

        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Map<Date, Integer> buffer = getDataFromBuffer(recentData, route, providers, hash);

        if (buffer == null || buffer.isEmpty()) {
            Properties properties = getProperties();
            long timeframe = Integer.parseInt(properties.getProperty("RecentDataTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            //calculate
            ITrafficDataDAO dao = beans.getTrafficDataDAO();
            List<IRouteData> data = dao.getData(route, startDate, endDate, providers);
            if (data == null || data.isEmpty()) {
                buffer = new HashMap<>();
                //logger.exiting("DataProvider", "getRecentData", buffer);
                return buffer;
            }

            Map<Date, List<IRouteData>> sortedData = new HashMap<>();

            for (IRouteData d : data) {
                if (sortedData.containsKey(d.getTimestamp())) {
                    sortedData.get(d.getTimestamp()).add(d);
                } else {
                    List<IRouteData> arr = new ArrayList<>();
                    arr.add(d);
                    sortedData.put(d.getTimestamp(), arr);
                }
            }

            Map<Date, Integer> aggrData = new TreeMap<>();

            for (Map.Entry<Date, List<IRouteData>> entry : sortedData.entrySet()) {
                aggrData.put(entry.getKey(), getMeanDurationFromRouteData(entry.getValue()));
            }

            buffer = aggrData;

            setDataInBuffer(buffer, recentData, route, providers, hash, null);

        }

        if (buffer == null) {
            buffer = new HashMap<>();
        }
        //logger.exiting("DataProvider", "getRecentData", buffer);
        return buffer;
    }

    private Map<Long, Map<IRoute, Map<Weekdays, List<Integer>>>> dataByDay = new ConcurrentHashMap<>();

    @Override

    public Map<Weekdays, List<Integer>> getDataByDay(IRoute route, List<String> providers, Weekdays... days) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Map<Weekdays, List<Integer>> buffer = getDataFromBuffer(dataByDay, route, providers, hash);

        boolean recalc = false;
        if (buffer != null) {
            long hash1 = calculateHash(days);
            long hash2 = calculateHash(buffer.keySet());
            if (hash1 != hash2) {
                recalc = true;
            }
        }

        if (recalc || buffer == null || buffer.isEmpty()) {
            Properties properties = getProperties();
            long timeframe = Integer.parseInt(properties.getProperty("DataByDayTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getDataByDay(route, providers, startDate, endDate, days);
            setDataInBuffer(buffer, dataByDay, route, providers, hash, null);
        }

        if (buffer == null) {
            buffer = new HashMap<>();
        }
        return buffer;
    }

    private Map<Long, Map<IRoute, Map<Weekdays, List<Integer>>>> velocityByDay = new ConcurrentHashMap<>();

    @Override

    public Map<Weekdays, List<Integer>> getDataVelocityByDay(IRoute route, List<String> providers, Weekdays... days) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Map<Weekdays, List<Integer>> buffer = getDataFromBuffer(velocityByDay, route, providers, hash);

        if (buffer == null || buffer.isEmpty()) {
            Properties properties = getProperties();
            long timeframe = Integer.parseInt(properties.getProperty("DataByDayTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getDataVelocityByDay(route, providers, startDate, endDate, days);
            setDataInBuffer(buffer, velocityByDay, route, providers, hash, null);
        }

        if (buffer == null) {
            buffer = new HashMap<>();
        }

        return buffer;
    }

    @Override

    public Map<Weekdays, List<Integer>> getDataByDay(IRoute route, List<String> providers, Date start, Date end, Weekdays... days) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getDataByDay", new Object[]{route, providers, start, end});
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        ITrafficDataDAO dao = beans.getTrafficDataDAO();
        Map<Weekdays, List<Integer>> ret = new HashMap<>();

        GregorianCalendar startHour = new GregorianCalendar(0, 0, 0, 6, 0, 0);
        GregorianCalendar endHour = new GregorianCalendar(0, 0, 0, 2, 0);
        int perMinutes = 15;
        List<List<List<Date>>> list = generateListsForDayOfWeek(start, end, startHour, endHour, true);

        for (Weekdays day : days) {
            int weekday = day.ordinal();
            List<Long> data = dao.getAggregateData(route, providers, list.get(0).get(weekday), list.get(1).get(weekday), perMinutes * 60, true, new AggregationContainer(Aggregation.none, "timestamp"), new AggregationContainer(Aggregation.sum, "duration * distance"), new AggregationContainer(Aggregation.sum, "distance"));

            ret.put(day, MapDataByDay(data, perMinutes));
        }

        if (ret == null) {
            ret = new HashMap<>();
        }

        //logger.exiting("DataProvider", "getDataByDay", ret);
        return ret;
    }

    @Override

    public Map<Weekdays, List<Integer>> getDataVelocityByDay(IRoute route, List<String> providers, Date start, Date end, Weekdays... days) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getDataVelocityByDay", new Object[]{route, providers, start, end});
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        ITrafficDataDAO dao = beans.getTrafficDataDAO();
        Map<Weekdays, List<Integer>> ret = new HashMap<>();

        GregorianCalendar startHour = new GregorianCalendar(0, 0, 0, 6, 0, 0);
        GregorianCalendar endHour = new GregorianCalendar(0, 0, 0, 2, 0);
        int perMinutes = 15;
        List<List<List<Date>>> list = generateListsForDayOfWeek(start, end, startHour, endHour, true);

        for (Weekdays day : days) {
            int weekday = day.ordinal();
            List<Long> data = dao.getAggregateData(route, providers, list.get(0).get(weekday), list.get(1).get(weekday), perMinutes * 15, true, new AggregationContainer(Aggregation.none, "timestamp"), new AggregationContainer(Aggregation.sum, "distance * distance / duration "), new AggregationContainer(Aggregation.sum, "distance"));

            ret.put(day, MapDataByDay(data, perMinutes));
        }

        if (ret == null) {
            ret = new HashMap<>();
        }

        //logger.exiting("DataProvider", "getDataVelocityByDay", ret);
        return ret;
    }

    private List<Integer> MapDataByDay(List<Long> data, int increaseMinutes) {
        int index = 0;

        Map<Date, List<Integer>> d = new TreeMap<>();
        Map<Date, List<Integer>> d2 = new TreeMap<>();
        Calendar cal = new GregorianCalendar();
        cal.set(0, 0, 0, 6, 0, 0);
        while (cal.get(GregorianCalendar.HOUR_OF_DAY) != 0) {
            d.put(cal.getTime(), new ArrayList<Integer>());
            cal.add(GregorianCalendar.MINUTE, increaseMinutes);
        }
        cal = new GregorianCalendar();
        cal.set(0, 0, 0, 0, 0, 0);
        while (cal.get(GregorianCalendar.HOUR_OF_DAY) != 2) {
            d2.put(cal.getTime(), new ArrayList<Integer>());
            cal.add(GregorianCalendar.MINUTE, increaseMinutes);
        }
        d2.put(cal.getTime(), new ArrayList<Integer>());

        long dateDiff = (increaseMinutes * (long) 60 * 1000) / 2;
        while (index < data.size()) {
            Long da = data.get(index++);
            Long exp = data.get(index++);
            Long div = data.get(index++);

            //strip date, keep hours and minutes
            cal.setTime(new Date(da));
            cal.set(0, 0, 0, cal.get(GregorianCalendar.HOUR_OF_DAY), cal.get(GregorianCalendar.MINUTE), 0);
            Date date = cal.getTime();

            int res = -1;
            if (div != 0) {
                res = Math.toIntExact(exp / div);
            }

            if (res != -1) {
                for (Map.Entry<Date, List<Integer>> entry : d.entrySet()) {
                    Date thisDate = entry.getKey();
                    if (Math.abs(thisDate.getTime() - date.getTime()) <= dateDiff) {
                        entry.getValue().add(res);
                    }
                }
                for (Map.Entry<Date, List<Integer>> entry : d2.entrySet()) {
                    Date thisDate = entry.getKey();
                    if (Math.abs(thisDate.getTime() - date.getTime()) <= dateDiff) {
                        entry.getValue().add(res);
                    }
                }
            }
        }

        List<Integer> ret = new ArrayList<>(d.size() + d2.size());
        for (Map.Entry<Date, List<Integer>> entry : d.entrySet()) {
            int result = 0;
            List<Integer> list = entry.getValue();
            if (list.size() > 0) {
                for (Integer x : list) {
                    result += x;
                }
                result = result / list.size();
                ret.add(result);
            } else {
                ret.add(-1);
            }
        }
        for (Map.Entry<Date, List<Integer>> entry : d2.entrySet()) {
            int result = 0;
            List<Integer> list = entry.getValue();
            if (list.size() > 0) {
                for (Integer x : list) {
                    result += x;
                }
                result = result / list.size();
                ret.add(result);
            } else {
                ret.add(-1);
            }
        }

        /*for (int i = 6; i != 3; i = (i + 1) % 24) {
            arr.add(-1);
        }

        while (index < data.size()) {
            Date d = new Date(data.get(index++));
            cal.setTime(d);

            Long exp = data.get(index++);
            Long div = data.get(index++);
            int res = -1;
            if (div != 0) {
                res = Math.toIntExact(exp / div);
            }
            int hour = cal.get(GregorianCalendar.HOUR_OF_DAY);
            if (hour >= 6) {
                arr.set(hour - 6, res);
            } else if (hour <= 2 && hour >= 0) {
                arr.set(hour + 18, res);
            }
        }*/
        return ret;
    }

    protected Pattern timeFormat = Pattern.compile("([0-9]{2})-([0-9]{2})");

    private Calendar GetTimeFromPropertyValue(String property) {
        Matcher m = timeFormat.matcher(property);
        if (m.matches()) {
            int hour = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            Calendar c = new GregorianCalendar();
            c.set(0, 0, 0, hour, min, 0);
            return c;
        }
        return null;
    }

    //generate a list with all specific hours between 2 dates (for example all 06:00 bewteen 02/02/16 04/04/16)
    private List<Date> GenerateListForHourBetweenDates(Calendar hour, Date startDate, Date endDate) {
        List<Date> dates = new ArrayList<>();

        Calendar cStart = new GregorianCalendar();
        cStart.setTime(startDate);
        Calendar cEnd = new GregorianCalendar();
        cEnd.setTime(endDate);

        cStart.set(Calendar.HOUR_OF_DAY, hour.get(Calendar.HOUR_OF_DAY));
        cStart.set(Calendar.MINUTE, hour.get(Calendar.MINUTE));

        while (cStart.before(cEnd)) {
            dates.add(cStart.getTime());
            cStart.add(Calendar.DAY_OF_MONTH, 1);
        }
        return dates;
    }

    //List contains 2 list, 1 for startDates, other for endDates. In those dates there is a list for every day of the week.
    //nextday indicated that the endHour is in the next day
    private List<List<List<Date>>> generateListsForDayOfWeek(Date startDate, Date endDate, Calendar startHour, Calendar endHour, boolean nextDay) {
        List<List<List<Date>>> ret = new ArrayList<>();
        for (int j = 0; j < 2; j++) {
            ret.add(new ArrayList<List<Date>>());
            for (int i = 0; i < 7; i++) {
                ret.get(j).add(new ArrayList<Date>());
            }
        }

        GregorianCalendar start = new GregorianCalendar();
        GregorianCalendar end = new GregorianCalendar();

        start.setTime(startDate);
        //hour and minutes are adjusted, not seconds
        start.set(GregorianCalendar.SECOND, 0);
        start.set(GregorianCalendar.MINUTE, 0);
        start.set(GregorianCalendar.HOUR_OF_DAY, 0);

        end.setTime(endDate);
        end.set(GregorianCalendar.SECOND, 59);
        end.set(GregorianCalendar.MINUTE, 59);
        end.set(GregorianCalendar.HOUR_OF_DAY, 23);

        while (start.before(end)) {
            int weekday = start.get(GregorianCalendar.DAY_OF_WEEK);

            //correct weekday to 0 based index, and shift monday to first
            weekday -= 2;
            if (weekday < 0) {
                weekday = 6;
            }

            start.set(GregorianCalendar.HOUR_OF_DAY, startHour.get(GregorianCalendar.HOUR_OF_DAY));
            start.set(GregorianCalendar.MINUTE, startHour.get(GregorianCalendar.MINUTE));
            ret.get(0).get(weekday).add(start.getTime());

            //adds the endDate if the hour is in same day
            if (!nextDay) {
                start.set(GregorianCalendar.HOUR_OF_DAY, endHour.get(GregorianCalendar.HOUR_OF_DAY));
                start.set(GregorianCalendar.MINUTE, endHour.get(GregorianCalendar.MINUTE));
                ret.get(1).get(weekday).add(start.getTime());
            }

            start.add(GregorianCalendar.DAY_OF_MONTH, 1);

            //adds the endDate if the hour was in the next day
            if (nextDay) {
                start.set(GregorianCalendar.HOUR_OF_DAY, endHour.get(GregorianCalendar.HOUR_OF_DAY));
                start.set(GregorianCalendar.MINUTE, endHour.get(GregorianCalendar.MINUTE));
                ret.get(1).get(weekday).add(start.getTime());
            }

            //for eval of end, so that the last day is certainly included in the generation
            start.set(GregorianCalendar.HOUR_OF_DAY, 0);
        }

        return ret;
    }

    @Override
    public void invalidateCurrentData() {
        this.currentDuration.clear();
        this.currentSpeed.clear();
        this.distance.clear();
        this.trend.clear();
        this.recentData.clear();
    }

    private long calculateHash(List<String> providers) {
        if (providers == null) {
            return 0;
        }
        long hash = 0;
        for (String s : providers) {
            hash += s.hashCode();
        }
        return hash;
    }

    private long calculateHash(Weekdays[] days) {
        if (days == null) {
            return 0;
        }
        long hash = 0;
        for (Weekdays s : days) {
            hash += s.hashCode();
        }
        return hash;
    }

    private long calculateHash(Set<Weekdays> keySet) {
        if (keySet == null) {
            return 0;
        }
        long hash = 0;
        for (Weekdays s : keySet) {
            hash += s.hashCode();
        }
        return hash;
    }

    private List<Integer> mapDataByCombinedDay(Map<Weekdays, List<Integer>> data) {
        List<Integer> ret = null;
        List<Integer> count = new ArrayList<>();
        for (Map.Entry<Weekdays, List<Integer>> entry : data.entrySet()) {
            List<Integer> list = entry.getValue();
            if (ret == null) {
                ret = list;
                for (int i = 0; i < ret.size(); i++) {
                    if (ret.get(i) != -1) {
                        count.add(1);
                    } else {
                        count.add(1);
                    }
                }
            } else {
                for (int i = 0; i < ret.size(); i++) {
                    if (list.get(i) != -1) {
                        ret.set(i, ret.get(i) + list.get(i));
                        count.set(i, count.get(i) + 1);
                    }
                }
            }
        }
        for (int i = 0; i < ret.size(); i++) {
            ret.set(i, ret.get(i) / count.get(i));
        }
        return ret;
    }

    @Override

    public List<Integer> getDataByCombinedDay(IRoute route, List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        Map<Weekdays, List<Integer>> data = getDataByDay(route, providers, Weekdays.MONDAY, Weekdays.TUESDAY, Weekdays.WEDNESDAY, Weekdays.THURSDAY, Weekdays.FRIDAY);
        return mapDataByCombinedDay(data);
    }

    @Override

    public List<Integer> getDataVelocityByCombinedDay(IRoute route, List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        Map<Weekdays, List<Integer>> data = getDataVelocityByDay(route, providers, Weekdays.MONDAY, Weekdays.TUESDAY, Weekdays.WEDNESDAY, Weekdays.THURSDAY, Weekdays.FRIDAY);
        return mapDataByCombinedDay(data);
    }

    @Override

    public List<Integer> getDataByCombinedDay(IRoute route, List<String> providers, Date start, Date end) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        Map<Weekdays, List<Integer>> data = getDataByDay(route, providers, start, end, Weekdays.MONDAY, Weekdays.TUESDAY, Weekdays.WEDNESDAY, Weekdays.THURSDAY, Weekdays.FRIDAY);
        return mapDataByCombinedDay(data);
    }

    @Override

    public List<Integer> getDataVelocityByCombinedDay(IRoute route, List<String> providers, Date start, Date end) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        Map<Weekdays, List<Integer>> data = getDataVelocityByDay(route, providers, start, end, Weekdays.MONDAY, Weekdays.TUESDAY, Weekdays.WEDNESDAY, Weekdays.THURSDAY, Weekdays.FRIDAY);
        return mapDataByCombinedDay(data);
    }

    private DateFormat dateFormatter = new SimpleDateFormat("HH:mm");

    @Override

    public List<String> getDataByDayHours() {
        int index = 0;
        int hour = 6;
        int maxHour = 2;
        Calendar cal = new GregorianCalendar();
        cal.set(GregorianCalendar.HOUR_OF_DAY, hour);
        cal.set(GregorianCalendar.MINUTE, 0);
        cal.set(GregorianCalendar.SECOND, 0);
        List<String> arr = new ArrayList<>();

        while (cal.get(GregorianCalendar.HOUR_OF_DAY) != maxHour) {
            arr.add(dateFormatter.format(cal.getTime()));
            cal.add(GregorianCalendar.MINUTE, 15);
        }
        arr.add(dateFormatter.format(cal.getTime()));
        return arr;
    }

    private Map<Long, Map<IRoute, Map<Date, Integer>>> dataBuffer = new ConcurrentHashMap<>();

    @Override

    public Map<Date, Integer> getData(IRoute route, List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Map<Date, Integer> buffer = getDataFromBuffer(dataBuffer, route, providers, hash);

        if (buffer == null || buffer.isEmpty()) {
            Properties properties = getProperties();
            int precision = Integer.parseInt(properties.getProperty("DataDefaultPrecision", "100"));
            long timeframe = Integer.parseInt(properties.getProperty("DataTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getData(route, providers, precision, startDate, endDate);
            setDataInBuffer(buffer, dataBuffer, route, providers, hash, null);
        }

        if (buffer == null) {
            buffer = new HashMap<>();
        }

        return buffer;
    }

    private Map<Long, Map<IRoute, Map<Date, Integer>>> dataVelocityBuffer = new ConcurrentHashMap<>();

    @Override

    public Map<Date, Integer> getDataVelocity(IRoute route, List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        long hash = calculateHash(providers);
        Map<Date, Integer> buffer = getDataFromBuffer(dataVelocityBuffer, route, providers, hash);

        if (buffer == null || buffer.isEmpty()) {
            Properties properties = getProperties();
            int precision = Integer.parseInt(properties.getProperty("DataDefaultPrecision", "100"));
            long timeframe = Integer.parseInt(properties.getProperty("DataTimeFrame", "0")) * (long) 1000;
            long currentTime = beans.getTimer().getCurrentTime();
            Date startDate = new Date(currentTime - timeframe);
            Date endDate = new Date(currentTime);

            buffer = getDataVelocity(route, providers, precision, startDate, endDate);
            setDataInBuffer(buffer, dataVelocityBuffer, route, providers, hash, null);
        }

        if (buffer == null) {
            buffer = new HashMap<>();
        }

        return buffer;
    }

    @Override

    public Map<Date, Integer> getData(IRoute route, List<String> providers, int precision, Date start, Date end) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getData", new Object[]{route, providers, precision, start, end});
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        Map<Date, Integer> ret = new TreeMap<>();

        //round start and end to halfhour
        start = new Date((start.getTime() / 1800000) * 1800000);
        end = new Date((end.getTime() / 1800000) * 1800000);

        long precisionDistance = generatePrecisionDistance(start, end, precision);
        long halfPrecisionDistance = precisionDistance / 2;

        ITrafficDataDAO dao = beans.getTrafficDataDAO();

        Map<Long, Integer> mappedList = GenerateListForPrecisionPointBewteenDates(start, end, precisionDistance);

        precisionDistance /= 1000;
        List<Long> data = dao.getAggregateData(route, providers, start, end, precisionDistance, false, new AggregationContainer(Aggregation.none, "timestamp"), new AggregationContainer(Aggregation.sum, "duration * distance"), new AggregationContainer(Aggregation.sum, "distance"));

        //map all retrieved data
        Iterator<Map.Entry<Long, Integer>> iter = mappedList.entrySet().iterator();
        if (iter.hasNext()) {
            Map.Entry<Long, Integer> mappedEntry = iter.next();
            int index = 0;
            long lower = mappedEntry.getKey() - halfPrecisionDistance;
            long upper = mappedEntry.getKey() + halfPrecisionDistance;
            while (index < data.size()) {
                Long d = data.get(index++);

                Long exp = data.get(index++);
                Long div = data.get(index++);
                int res;
                if (div != 0) {
                    res = Math.toIntExact(exp / div);
                } else {
                    res = -1;
                }

                boolean found = false;
                while (!found) {
                    if (d < upper) {
                        found = true;
                        if (mappedEntry.getValue() == -1) {
                            mappedEntry.setValue(res);
                        } else {
                            mappedEntry.setValue((mappedEntry.getValue() + res) / 2);
                        }
                    } else if (iter.hasNext()) {
                        mappedEntry = iter.next();
                        lower = mappedEntry.getKey() - halfPrecisionDistance;
                        upper = mappedEntry.getKey() + halfPrecisionDistance;
                    } else {
                        found = true;
                    }
                }
            }
        }

        Map<Date, Integer> r = new TreeMap<>();
        for (Map.Entry<Long, Integer> set : mappedList.entrySet()) {
            r.put(new Date(set.getKey()), set.getValue());
        }

        //logger.exiting("DataProvider", "getData", ret);
        return r;
    }

    @Override

    public Map<Date, Integer> getDataVelocity(IRoute route, List<String> providers, int precision, Date start, Date end) {
        //ILogger logger = beans.getLogger();
        //logger.entering("DataProvider", "getDataVelocity", new Object[]{route, providers, precision, start, end});
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }

        Map<Date, Integer> ret = new TreeMap<>();

        //round start and end to halfhour
        start = new Date((start.getTime() / 1800000) * 1800000);
        end = new Date((end.getTime() / 1800000) * 1800000);

        long precisionDistance = generatePrecisionDistance(start, end, precision);
        long halfPrecisionDistance = precisionDistance / 2;

        ITrafficDataDAO dao = beans.getTrafficDataDAO();

        Map<Long, Integer> mappedList = GenerateListForPrecisionPointBewteenDates(start, end, precisionDistance);

        precisionDistance /= 1000;
        List<Long> data = dao.getAggregateData(route, providers, start, end, precisionDistance / 1000, false, new AggregationContainer(Aggregation.none, "timestamp"), new AggregationContainer(Aggregation.sum, "distance * distance / duration "), new AggregationContainer(Aggregation.sum, "distance"));

        //map all retrieved data
        Iterator<Map.Entry<Long, Integer>> iter = mappedList.entrySet().iterator();
        if (iter.hasNext()) {
            Map.Entry<Long, Integer> mappedEntry = iter.next();
            int index = 0;
            long lower = mappedEntry.getKey() - halfPrecisionDistance;
            long upper = mappedEntry.getKey() + halfPrecisionDistance;
            while (index < data.size()) {
                Long d = data.get(index++);

                Long exp = data.get(index++);
                Long div = data.get(index++);
                int res;
                if (div != 0) {
                    res = Math.toIntExact(exp / div);
                } else {
                    res = -1;
                }

                boolean found = false;
                while (!found) {
                    if (d < upper) {
                        found = true;
                        if (mappedEntry.getValue() == -1) {
                            mappedEntry.setValue(res);
                        } else {
                            mappedEntry.setValue((mappedEntry.getValue() + res) / 2);
                        }
                    } else if (iter.hasNext()) {
                        mappedEntry = iter.next();
                        lower = mappedEntry.getKey() - halfPrecisionDistance;
                        upper = mappedEntry.getKey() + halfPrecisionDistance;
                    } else {
                        found = true;
                    }
                }
            }
        }

        Map<Date, Integer> r = new TreeMap<>();
        for (Map.Entry<Long, Integer> set : mappedList.entrySet()) {
            r.put(new Date(set.getKey()), set.getValue());
        }

        //logger.exiting("DataProvider", "getData", ret);
        return r;
    }

    @Override

    public Map<Date, Integer> getData(IRoute route, List<String> providers, int precision) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }
        Properties properties = getProperties();
        long timeframe = Integer.parseInt(properties.getProperty("DataTimeFrame", "0")) * (long) 1000;
        long currentTime = beans.getTimer().getCurrentTime();
        Date startDate = new Date(currentTime - timeframe);
        Date endDate = new Date(currentTime);

        return getData(route, providers, precision, startDate, endDate);
    }

    @Override

    public Map<Date, Integer> getDataVelocity(IRoute route, List<String> providers, int precision) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }
        Properties properties = getProperties();
        long timeframe = Integer.parseInt(properties.getProperty("DataTimeFrame", "0")) * (long) 1000;
        long currentTime = beans.getTimer().getCurrentTime();
        Date startDate = new Date(currentTime - timeframe);
        Date endDate = new Date(currentTime);

        return getDataVelocity(route, providers, precision, startDate, endDate);
    }

    @Override

    public Map<Date, Integer> getData(IRoute route, List<String> providers, Date start, Date end) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }
        Properties properties = getProperties();
        int precision = Integer.parseInt(properties.getProperty("DataDefaultPrecision", "100"));

        return getData(route, providers, precision, start, end);
    }

    @Override

    public Map<Date, Integer> getDataVelocity(IRoute route, List<String> providers, Date start, Date end) {
        if (providers == null || providers.isEmpty()) {
            providers = beans.getPropertiesCollection().getDefaultProviders();
        }
        Properties properties = getProperties();
        int precision = Integer.parseInt(properties.getProperty("DataDefaultPrecision", "100"));

        return getDataVelocity(route, providers, precision, start, end);
    }

    private Map<Long, Integer> GenerateListForPrecisionPointBewteenDates(Date start, Date end, long precisionDistance) {
        Map<Long, Integer> ret = new TreeMap<>();
        long t = start.getTime();
        long stop = end.getTime();

        while (t <= stop) {
            ret.put(t, -1);
            t += precisionDistance;
        }
        return ret;
    }

    private long generatePrecisionDistance(Date start, Date end, int precision) {
        long precisionDistance = end.getTime() - start.getTime();
        precisionDistance /= precision;

        //5min min distance
        precisionDistance = Math.max(1L, precisionDistance / 300000);
        precisionDistance *= 300000;

        int[] snapPoints = new int[]{1800000, 3600000, 7200000, 21600000, 43200000, 86400000, 604800000};

        for (int snap : snapPoints) {
            if (precisionDistance >= (snap * precision)) {
                precisionDistance = (precisionDistance / snap) * snap;
            } else {
                return precisionDistance;
            }
        }

        return precisionDistance;
    }

    @Override
    public void invalidateBuffers() {
        this.avgDuration.clear();
        this.avgSpeed.clear();
        this.dataBuffer.clear();
        this.dataByDay.clear();
        this.dataVelocityBuffer.clear();
        this.distance.clear();
        this.optimalDuration.clear();
        this.optimalSpeed.clear();
        this.velocityByDay.clear();
    }

}
