package org.onosproject.segmentrouting;

/**
 * Created by john on 2016/07/20.
 */
import org.onosproject.segmentrouting.grouphandler.DefaultGroupHandler;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;



public class FailoverRules {
    protected static final Logger log = getLogger(DefaultGroupHandler.class);

    /**
     * Removes the policy given.
     *
     * @return POLICY_NOT_FOUND if the policy to remove does not exists,
     * SUCCESS if it is removed successfully
     */
    public boolean addFailover() {
        log.info("Failover rules fake added");
        return true;
    }
}
