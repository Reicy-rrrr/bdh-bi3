package com.deloitte.bdh.data.collation.integration;

import java.util.Map;

public interface XxJobService {
    /**
     * ADD
     */
    String ADD_PATH = "/bdh-job-admin/bdhJob/addBdhJobObj";

    /**
     * UPDATE
     */
    String UPDATE_PATH = "/bdh-job-admin/bdhJob/updateObj";

    /**
     * REMOVE
     */
    String REMOVE_PATH = "/bdh-job-admin/bdhJob/remove?jobDesc=";

    /**
     * START
     */
    String START_PATH = "/bdh-job-admin/bdhJob/start?jobDesc=";


    /**
     * STOP
     */
    String STOP_PATH = "/bdh-job-admin/bdhJob/stop?jobDesc=";

    /**
     * trigger
     */
    String TRIGGER_PATH = "/bdh-job-admin/bdhJob/trigger?jobDesc=";

    /**
     * add
     *
     * @param
     * @return
     */
    void add(String modelCode, String callBackAddress, String cron, Map<String, String> params) throws Exception;

    /**
     * update
     *
     * @param
     * @return
     */
    void update(String modelCode, String callBackAddress, String cron, Map<String, String> params) throws Exception;


    /**
     * remove
     *
     * @param
     * @return
     */
    void remove(String modelCode) throws Exception;


    /**
     * start
     *
     * @param
     * @return
     */
    void start(String modelCode) throws Exception;


    /**
     * stop
     *
     * @param
     * @return
     */
    void stop(String modelCode) throws Exception;

    /**
     * trigger
     *
     * @param
     * @return
     */
    void trigger(String modelCode) throws Exception;

}