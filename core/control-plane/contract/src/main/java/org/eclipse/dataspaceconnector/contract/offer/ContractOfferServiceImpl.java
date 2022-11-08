/*
 *  Copyright (c) 2021 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial API and Implementation
 *       Microsoft Corporation - Refactoring
 *       Fraunhofer Institute for Software and Systems Engineering - extended method implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.dataspaceconnector.contract.offer;

import org.eclipse.dataspaceconnector.policy.model.Policy;
import org.eclipse.dataspaceconnector.spi.agent.ParticipantAgentService;
import org.eclipse.dataspaceconnector.spi.asset.AssetIndex;
import org.eclipse.dataspaceconnector.spi.contract.ContractId;
import org.eclipse.dataspaceconnector.spi.contract.offer.ContractDefinitionService;
import org.eclipse.dataspaceconnector.spi.contract.offer.ContractOfferQuery;
import org.eclipse.dataspaceconnector.spi.contract.offer.ContractOfferService;
import org.eclipse.dataspaceconnector.spi.message.Range;
import org.eclipse.dataspaceconnector.spi.policy.store.PolicyDefinitionStore;
import org.eclipse.dataspaceconnector.spi.query.QuerySpec;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractDefinition;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractOffer;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the {@link ContractOfferService}.
 */
public class ContractOfferServiceImpl implements ContractOfferService {
    private final ParticipantAgentService agentService;
    private final ContractDefinitionService definitionService;
    private final AssetIndex assetIndex;
    private final PolicyDefinitionStore policyStore;

    public ContractOfferServiceImpl(ParticipantAgentService agentService, ContractDefinitionService definitionService, AssetIndex assetIndex, PolicyDefinitionStore policyStore) {
        this.agentService = agentService;
        this.definitionService = definitionService;
        this.assetIndex = assetIndex;
        this.policyStore = policyStore;
    }

    @Override
    @NotNull
    public Stream<ContractOffer> queryContractOffers(ContractOfferQuery query, Range range) {
        var agent = agentService.createFor(query.getClaimToken());
        var numSeenAssets = new AtomicLong(0);
        var limit = range.getTo() - range.getFrom();
        var skip = new AtomicInteger(range.getFrom());

        return definitionService.definitionsFor(agent)
                .takeWhile(d -> numSeenAssets.get() < range.getTo())
                .flatMap(definition -> {
                    var criteria = definition.getSelectorExpression().getCriteria();

                    var querySpecBuilder = QuerySpec.Builder.newInstance()
                            .filter(Stream.concat(criteria.stream(), query.getAssetsCriteria().stream()).collect(Collectors.toList()));

                    var querySpec = querySpecBuilder.build();
                    var numAssets = assetIndex.countAssets(querySpec.getFilterExpression());

                    querySpecBuilder.limit((int) numAssets);

                    if (skip.get() > 0) {
                        querySpecBuilder.offset(skip.get());
                    }
                    if (numAssets + numSeenAssets.get() > limit) {
                        querySpecBuilder.limit(limit);
                    }

                    Stream<ContractOffer> offers;
                    if (skip.get() >= numAssets) {
                        offers = Stream.empty();
                    } else {
                        offers = createContractOffers(definition, querySpecBuilder.build());
                    }

                    numSeenAssets.addAndGet(numAssets);
                    skip.addAndGet(Long.valueOf(-numAssets).intValue());
                    return offers;
                });
    }

    @NotNull
    private Stream<@NotNull ContractOffer> createContractOffers(ContractDefinition definition, QuerySpec assetQuerySpec) {
        return Optional.of(definition.getContractPolicyId())
                .map(policyStore::findById)
                .map(policyDefinition -> assetIndex.queryAssets(assetQuerySpec)
                        .map(asset -> createContractOffer(definition, policyDefinition.getPolicy(), asset)))
                .orElse(Stream.empty());
    }

    @NotNull
    private ContractOffer createContractOffer(ContractDefinition definition, Policy policy, Asset asset) {
        return ContractOffer.Builder.newInstance()
                .id(ContractId.createContractId(definition.getId()))
                .policy(policy.withTarget(asset.getId()))
                .asset(asset)
                // TODO: this is a workaround for the bug described in https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/issues/753
                .provider(URI.create("urn:connector:provider"))
                .consumer(URI.create("urn:connector:consumer"))
                .build();
    }
}
