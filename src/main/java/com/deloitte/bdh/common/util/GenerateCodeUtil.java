package com.deloitte.bdh.common.util;


/**
 * UUID工具类
 *
 * @author dahpeng
 * @date 2019/05/22
 */
public class GenerateCodeUtil {

    private static SnowFlakeUtil util = new SnowFlakeUtil(0, 0);
    private static final String PREFIX_MODEL_ = "Model_";
    private static final String PREFIX_COMPONENT_ = "COMP_";
    private static final String PREFIX_PROCESSORS_ = "PROS_";
    private static final String PREFIX_PARAMS_ = "PARAM_";
    private static final String PREFIX_CONNECTS_ = "CONS_";
    private static final String PREFIX_PAGE_ = "PAGE_";
    private static final String PREFIX_SHOT_ = "SHOT_";

    private GenerateCodeUtil() {
    }

    public static String genModel() {
        return generate(PREFIX_MODEL_);
    }

    public static String genPage() {
        return generate(PREFIX_PAGE_);
    }

    public static String genShot() {
        return generate(PREFIX_SHOT_);
    }

    public static String getComponent() {
        return generate(PREFIX_COMPONENT_);
    }

    public static String genProcessors() {
        return generate(PREFIX_PROCESSORS_);
    }

    public static String genParam() {
        return generate(PREFIX_PARAMS_);
    }

    public static String genConnects() {
        return generate(PREFIX_CONNECTS_);
    }

    public static String generate() {
        return util.nextId() + "";
    }

    private static String generate(String prefix) {
        return prefix + util.nextId();
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(GenerateCodeUtil.genModel());
        }
    }
}
