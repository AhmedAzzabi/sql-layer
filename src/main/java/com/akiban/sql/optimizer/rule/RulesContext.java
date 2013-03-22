
package com.akiban.sql.optimizer.rule;

import org.slf4j.Logger;

import java.util.List;
import java.util.Properties;

/** The context / owner of a {@link PlanContext}, shared among several of them. */
public class RulesContext
{
    // TODO: Need more much sophisticated invocation mechanism.
    private Properties properties;
    private List<? extends BaseRule> rules;

    protected RulesContext() {
    }

    protected void initProperties(Properties properties) {
        this.properties = properties;
    }

    protected void initRules(List<? extends BaseRule> rules) {
        this.rules = rules;
    }

    protected void initDone() {
        assert (properties != null) : "initProperties() not called";
        assert (rules != null) : "initRules() not called";
    }

    protected boolean rulesAre(List<? extends BaseRule> expected) {
        return rules == expected;
    }

    /** Make context with these rules. Just for testing. */
    public static RulesContext create(List<? extends BaseRule> rules,
                                      Properties properties) {
        RulesContext context = new RulesContext();
        context.initProperties(properties);
        context.initRules(rules);
        context.initDone();
        return context;
    }

    public void applyRules(PlanContext plan) {
        boolean logged = false;
        for (BaseRule rule : rules) {
            Logger logger = rule.getLogger();
            boolean debug = logger.isDebugEnabled();
            if (debug && !logged) {
                logger.debug("Before {}:\n{}", rule.getName(), plan.getPlan());
            }
            beginRule(rule);
            try {
                rule.apply(plan);
            }
            catch (RuntimeException e) {
                if (debug) {
                    String msg = "error while applying " + rule.getName() + " to " + plan.getPlan();
                    logger.debug(msg, e);
                }
                throw e;
            }
            finally {
                endRule(rule);
            }
            if (debug) {
                logger.debug("After {}:\n{}", rule.getName(), plan.getPlan());
            }
            logged = debug;
        }
    }

    /** Extend this to implement tracing, etc. */
    public void beginRule(BaseRule rule) {
    }
    public void endRule(BaseRule rule) {
    }

    /** Get optimizer configuration. */
    public Properties getProperties() {
        return properties;
    }
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    public String getProperty(String key, String defval) {
        return properties.getProperty(key, defval);
    }
}
