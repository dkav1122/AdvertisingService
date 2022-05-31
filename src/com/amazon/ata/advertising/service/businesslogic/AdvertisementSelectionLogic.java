package com.amazon.ata.advertising.service.businesslogic;

import com.amazon.ata.advertising.service.dao.ReadableDao;
import com.amazon.ata.advertising.service.model.AdvertisementContent;
import com.amazon.ata.advertising.service.model.EmptyGeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.GeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.RequestContext;
import com.amazon.ata.advertising.service.targeting.TargetingEvaluator;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible for picking the advertisement to be rendered.
 */
public class AdvertisementSelectionLogic {

    private static final Logger LOG = LogManager.getLogger(AdvertisementSelectionLogic.class);

    private final ReadableDao<String, List<AdvertisementContent>> contentDao;
    private final ReadableDao<String, List<TargetingGroup>> targetingGroupDao;
    private Random random = new Random();

    /**
     * Constructor for AdvertisementSelectionLogic.
     * @param contentDao Source of advertising content.
     * @param targetingGroupDao Source of targeting groups for each advertising content.
     */
    @Inject
    public AdvertisementSelectionLogic(ReadableDao<String, List<AdvertisementContent>> contentDao,
                                       ReadableDao<String, List<TargetingGroup>> targetingGroupDao) {
        this.contentDao = contentDao;
        this.targetingGroupDao = targetingGroupDao;
    }

    /**
     * Setter for Random class.
     * @param random generates random number used to select advertisements.
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Gets all of the content and metadata for the marketplace and determines which content can be shown.  Returns the
     * eligible content with the highest click through rate.  If no advertisement is available or eligible, returns an
     * EmptyGeneratedAdvertisement.
     *
     * @param customerId - the customer to generate a custom advertisement for
     * @param marketplaceId - the id of the marketplace the advertisement will be rendered on
     * @return an advertisement customized for the customer id provided, or an empty advertisement if one could
     *     not be generated.
     */
    public GeneratedAdvertisement selectAdvertisement(String customerId, String marketplaceId) {
        GeneratedAdvertisement generatedAdvertisement = new EmptyGeneratedAdvertisement();
        TargetingEvaluator targetingEvaluator = new TargetingEvaluator(new RequestContext(customerId, marketplaceId));

        if (StringUtils.isEmpty(marketplaceId)) {
            LOG.warn("MarketplaceId cannot be null or empty. Returning empty ad.");
        } else {
            final List<AdvertisementContent> contents = contentDao.get(marketplaceId);

            final List<AdvertisementContent> eligibleContents;

            eligibleContents = contents.stream()
                    .filter(content -> this.contentFilter(content, targetingEvaluator))
                    .collect(Collectors.toList());

            TreeMap<Double, AdvertisementContent> adsSortedByCTR;
            adsSortedByCTR = new TreeMap<>(Comparator.reverseOrder());

            populateTreeByCTR(eligibleContents, adsSortedByCTR);





           if (CollectionUtils.isNotEmpty(eligibleContents)) {
               AdvertisementContent advertisementContentWithHighestCTR = adsSortedByCTR.firstEntry().getValue();
               generatedAdvertisement = new GeneratedAdvertisement(advertisementContentWithHighestCTR);

            }

        }

        return generatedAdvertisement;
    }

    private void populateTreeByCTR(List<AdvertisementContent> eligibleContents, TreeMap<Double, AdvertisementContent> tree) {
//        for(AdvertisementContent eligibleAd : eligibleContents) {
//            List<TargetingGroup> targetingGroups = targetingGroupDao.get(eligibleAd.getContentId());
//            double highestClickThroughRate = Integer.MIN_VALUE;
//            //for each targeting group, if its click through rate is higher than the current highest, make it the new highest CTR
//            for(TargetingGroup targetingGroup : targetingGroups) {
//                double clickThroughRate = targetingGroup.getClickThroughRate();
//                highestClickThroughRate = Math.max(clickThroughRate, highestClickThroughRate);
//            }
//            tree.put(highestClickThroughRate, eligibleAd);
//        }

       eligibleContents.stream().forEach(content -> getHighestCTR(content, tree));

    }

    private void getHighestCTR(AdvertisementContent content, TreeMap<Double, AdvertisementContent> tree) {
       Optional<Double> highestCTR = targetingGroupDao.get(content.getContentId())
                .stream()
                .map(targetingGroup -> targetingGroup.getClickThroughRate())
                .reduce((t1, t2) ->
                    t1 > t2? t1:t2
    );
       if(highestCTR.isPresent()) {
           tree.put(highestCTR.get(), content);
       }

       tree.put(0.0, content);
    }



    private boolean contentFilter(AdvertisementContent content, TargetingEvaluator targetingEvaluator) {

         List<TargetingGroup> eligibleResults = targetingGroupDao.get(content.getContentId())
                .stream()
                .filter(targetingGroup -> targetingEvaluator.evaluate(targetingGroup).isTrue())
                .collect(Collectors.toList());

         return eligibleResults.size() > 0;
    }
}
