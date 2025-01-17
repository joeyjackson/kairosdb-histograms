/**
 * Copyright 2019 Dropbox Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.kairosdb;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.kairosdb.core.KairosDataPointFactory;
import org.kairosdb.plugin.Aggregator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Provider;

/**
 * Provides mapping of data types and data groups to the aggregators that can aggregate them.
 *
 * @param <T> type of aggregator that this class maps to
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class GenericAggregatorMap<T extends Aggregator> {
    private final List<Provider<? extends T>> _providers;
    private final Map<String, Optional<Provider<? extends T>>> _groupMap = Maps.newConcurrentMap();
    private final Map<String, Optional<Provider<? extends T>>> _datastoreDataTypeMap = Maps.newConcurrentMap();
    private final KairosDataPointFactory _dataPointFactory;

    /**
     * Public constructor.
     *
     * @param dataPointFactory Factory for creating data points.
     * @param aggregators The aggregators to map to.
     */
    public GenericAggregatorMap(
            final KairosDataPointFactory dataPointFactory,
            final List<Provider<? extends T>> aggregators) {
        _providers = Lists.newArrayList(aggregators);
        _dataPointFactory = dataPointFactory;
    }

    /**
     * Gets an aggregator by group type.
     *
     * @param groupType the group type
     * @return aggregator to use
     */
    public Optional<Provider<? extends T>> aggregatorForGroupType(final String groupType) {
        return _groupMap.computeIfAbsent(groupType, this::findAggregatorByGroupType);
    }

    /**
     * Gets an aggregator by data store data type.
     *
     * @param dataStoreDataType the data store data type
     * @return aggregator to use
     */
    public Optional<T> aggregatorForDataStoreDataType(final String dataStoreDataType) {
        return _datastoreDataTypeMap.computeIfAbsent(dataStoreDataType, this::findAggregatorByDataStoreDataType).map(Provider::get);
    }

    private Optional<Provider<? extends T>> findAggregatorByDataStoreDataType(final String dataStoreDataType) {
        final String groupType = _dataPointFactory.getFactoryForDataStoreType(dataStoreDataType).getGroupType();
        return aggregatorForGroupType(groupType);
    }

    private Optional<Provider<? extends T>> findAggregatorByGroupType(final String groupType) {
        for (final Provider<? extends T> provider : _providers) {
            final T aggregator = provider.get();
            if (aggregator.canAggregate(groupType)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
