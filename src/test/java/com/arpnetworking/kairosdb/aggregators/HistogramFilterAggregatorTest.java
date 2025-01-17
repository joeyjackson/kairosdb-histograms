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
package com.arpnetworking.kairosdb.aggregators;

import com.arpnetworking.kairosdb.HistogramDataPoint;
import com.arpnetworking.kairosdb.HistogramDataPointImpl;
import com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kairosdb.core.DataPoint;
import org.kairosdb.core.aggregator.FilterAggregator;
import org.kairosdb.core.datapoints.DoubleDataPoint;
import org.kairosdb.core.datastore.DataPointGroup;
import org.kairosdb.testing.ListDataPointGroup;

import java.util.TreeMap;

/**
 * Unit tests for the Histogram Filter Aggregator.
 *
 * @author Joey Jackson (jjackson at dropbox dot com)
 */
public class HistogramFilterAggregatorTest {
    private HistogramFilterAggregator _aggregator;

    private static final double NEG_516_0 = HistogramFilterAggregator.truncate(-516.0);
    private static final double NEG_512_0 = HistogramFilterAggregator.truncate(-512.0);
    private static final double NEG_100_5 = HistogramFilterAggregator.truncate(-100.5);
    private static final double NEG_100_01 = -100.01; // Middle of bin
    private static final double NEG_100_0 = HistogramFilterAggregator.truncate(-100.0);
    private static final double NEG_99_5 = HistogramFilterAggregator.truncate(-99.5);
    private static final double NEG_1EN310 = nextLargestBin(-0.0);
    private static final double NEG_0_0 = HistogramFilterAggregator.truncate(-0.0);
    private static final double POS_0_0 = HistogramFilterAggregator.truncate(0.0);
    private static final double POS_1EN310 = nextLargestBin(0.0);
    private static final double POS_99_5 = HistogramFilterAggregator.truncate(99.5);
    private static final double POS_100_0 = HistogramFilterAggregator.truncate(100.0);
    private static final double POS_100_01 = 100.01; // Middle of bin
    private static final double POS_100_5 = HistogramFilterAggregator.truncate(100.5);
    private static final double POS_512_0 = HistogramFilterAggregator.truncate(512.0);
    private static final double POS_516_0 = HistogramFilterAggregator.truncate(516);

    private static double nextLargestBin(final double val) {
        long bound = Double.doubleToLongBits(val);
        bound >>= 45;
        bound += 1;
        bound <<= 45;
        return Double.longBitsToDouble(bound);
    }

    private void assertGroupsEqual(final DataPointGroup expected, final DataPointGroup actual) {
        while (expected.hasNext()) {
            Assert.assertTrue("Actual group is missing data points", actual.hasNext());
            final DataPoint act = actual.next();
            final DataPoint exp = expected.next();
            Assert.assertEquals("Expected and actual timestamps do not match", exp.getTimestamp(),
                    act.getTimestamp());
            assertHistogramsEqual(exp, act);
        }
        Assert.assertFalse("Actual group has too many data points", actual.hasNext());
    }
    
    private void assertHistogramsEqual(final DataPoint expected, final DataPoint actual) {
        Assert.assertTrue("Data point not an instance of class HistogramDataPoint",
                expected instanceof HistogramDataPoint);
        Assert.assertTrue("Data point not an instance of class HistogramDataPoint",
                actual instanceof HistogramDataPoint);
        final HistogramDataPoint hist1 = (HistogramDataPoint) expected;
        final HistogramDataPoint hist2 = (HistogramDataPoint) actual;

        Assert.assertEquals("Histograms did not match", hist1.getMap(), hist2.getMap());
        Assert.assertEquals(hist1.getSampleCount(), hist2.getSampleCount());
        Assert.assertEquals(hist1.getSum(), hist2.getSum(), 0);
        Assert.assertEquals(hist1.getMin(), hist2.getMin(), 0);
        Assert.assertEquals(hist1.getMax(), hist2.getMax(), 0);
    }

    private ListDataPointGroup createGroup(final DataPoint... dataPoints) {
        final ListDataPointGroup group = new ListDataPointGroup("test_values");
        for (DataPoint dp : dataPoints) {
            group.addDataPoint(dp);
        }
        return group;
    }

    private HistogramDataPoint createHistogram(final long timeStamp, final Double... values) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        double count = 0;
        final TreeMap<Double, Integer> bins = Maps.newTreeMap();
        for (final Double value : values) {
            sum += HistogramFilterAggregator.truncate(value);
            min = Math.min(min, Math.min(value, HistogramFilterAggregator.binInclusiveBound(value)));
            max = Math.max(max, Math.max(value, HistogramFilterAggregator.binInclusiveBound(value)));
            bins.compute(HistogramFilterAggregator.truncate(value), (i, j) -> j == null ? 1 : j + 1);
            count++;
        }
        final double mean = sum / count;
        return new HistogramDataPointImpl(timeStamp, 7, bins, min, max, mean, sum);
    }

    private HistogramDataPoint createExactHistogram(final long timeStamp, final Double... values) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        double count = 0;
        final TreeMap<Double, Integer> bins = Maps.newTreeMap();
        for (final Double value : values) {
            sum += HistogramFilterAggregator.truncate(value);
            min = Math.min(min, value);
            max = Math.max(max, value);
            bins.compute(HistogramFilterAggregator.truncate(value), (i, j) -> j == null ? 1 : j + 1);
            count++;
        }
        final double mean = sum / count;
        return new HistogramDataPointImpl(timeStamp, 7, bins, min, max, mean, sum);
    }

    private void runTest(final FilterAggregator.FilterOperation op,
                         final HistogramFilterAggregator.FilterIndeterminate ind,
                         final boolean isAtBoundaryTest, final boolean isPositiveTest,
                         final ListDataPointGroup expected) {
        final ListDataPointGroup group;
        final double threshold;
        if (isPositiveTest) {
            group = createGroup(createHistogram(1L, POS_99_5, POS_100_0, POS_100_5),
                    createHistogram(2L, POS_0_0, POS_512_0, POS_516_0));
            if (isAtBoundaryTest) {
                threshold = POS_100_0;
            } else {
                threshold = POS_100_01;
            }
        } else {
            group = createGroup(createHistogram(1L, NEG_99_5, NEG_100_0, NEG_100_5),
                    createHistogram(2L, NEG_0_0, NEG_512_0, NEG_516_0));
            if (isAtBoundaryTest) {
                threshold = NEG_100_0;
            } else {
                threshold = NEG_100_01;
            }
        }
        runTest(op, ind, threshold, group, expected);
    }

    private void runTest(final FilterAggregator.FilterOperation op,
                         final HistogramFilterAggregator.FilterIndeterminate ind,
                         final double threshold,
                         final ListDataPointGroup input,
                         final ListDataPointGroup expected) {
        _aggregator.setFilterOp(op);
        _aggregator.setFilterIndeterminateInclusion(ind);
        _aggregator.setThreshold(threshold);
        final DataPointGroup results = _aggregator.aggregate(input);
        assertGroupsEqual(expected, results);
    }

    @Before
    public void setUp() {
        _aggregator = new HistogramFilterAggregator();
    }

    @Test(expected = NullPointerException.class)
    public void testFilterNull() {
        _aggregator.aggregate(null);
    }

    @Test
    public void testFilterEmptyGroup() {
        final DataPointGroup group = createGroup();
        final DataPointGroup results = _aggregator.aggregate(group);
        Assert.assertFalse("Actual group was not empty", results.hasNext());
    }

    @Test
    public void testFilterNotHistogramDataPoint() {
        final ListDataPointGroup group = createGroup(
                new DoubleDataPoint(1L, 100.0),
                new DoubleDataPoint(2L, 100.0)
        );

        final ListDataPointGroup expected = createGroup();

        final DataPointGroup results = _aggregator.aggregate(group);
        assertGroupsEqual(expected, results);
    }

    @Test
    public void testFilterRemoveAllBins() {
        final ListDataPointGroup group = createGroup(createHistogram(1L, POS_100_0, POS_512_0, POS_516_0),
                createHistogram(2L, POS_100_0, POS_512_0, POS_516_0));

        _aggregator.setFilterOp(FilterAggregator.FilterOperation.GTE);
        _aggregator.setFilterIndeterminateInclusion(HistogramFilterAggregator.FilterIndeterminate.KEEP);
        _aggregator.setThreshold(POS_0_0);

        final ListDataPointGroup expected = createGroup();
        final DataPointGroup results = _aggregator.aggregate(group);
        assertGroupsEqual(expected, results);
    }

    @Test
    public void testFilterAroundZero() {
        DataPointGroup group, expected, results;
        _aggregator.setFilterIndeterminateInclusion(HistogramFilterAggregator.FilterIndeterminate.KEEP);

        group = createGroup(createHistogram(1L, POS_1EN310, POS_0_0, NEG_0_0, NEG_1EN310));
        _aggregator.setFilterOp(FilterAggregator.FilterOperation.LTE);
        _aggregator.setThreshold(POS_0_0);
        expected = createGroup(createHistogram(1L, POS_1EN310, POS_0_0));
        results = _aggregator.aggregate(group);
        assertGroupsEqual(expected, results);

        group = createGroup(createHistogram(1L, POS_1EN310, POS_0_0, NEG_0_0, NEG_1EN310));
        _aggregator.setFilterOp(FilterAggregator.FilterOperation.GTE);
        _aggregator.setThreshold(NEG_0_0);
        expected = createGroup(createHistogram(1L, NEG_0_0, NEG_1EN310));
        results = _aggregator.aggregate(group);
        assertGroupsEqual(expected, results);
    }

    @Test
    public void testFilterLessThanKeepThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.LT, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, true, 
                createGroup(createHistogram(1L, POS_100_0, POS_100_5),
                        createHistogram(2L, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterLessThanKeepThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.LT, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, false, 
                createGroup(createHistogram(1L, NEG_99_5, NEG_100_0),
                        createHistogram(2L, NEG_0_0)));
    }

    @Test
    public void testFilterLessThanKeepThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.LT, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, true,
                createGroup(createHistogram(1L, POS_100_0, POS_100_5),
                        createHistogram(2L, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterLessThanKeepThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.LT, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, false,
                createGroup(createHistogram(1L, NEG_99_5, NEG_100_0),
                        createHistogram(2L, NEG_0_0)));
    }

    @Test
    public void testFilterLessThanDiscardThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.LT, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, true,
                createGroup(createHistogram(1L, POS_100_0, POS_100_5),
                        createHistogram(2L, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterLessThanDiscardThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.LT, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, false,
                createGroup(createHistogram(1L, NEG_99_5), createHistogram(2L, NEG_0_0)));
    }

    @Test
    public void testFilterLessThanDiscardThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.LT, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, true,
                createGroup(createHistogram(1L, POS_100_5),
                        createHistogram(2L, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterLessThanDiscardThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.LT, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, false,
                createGroup(createHistogram(1L, NEG_99_5), createHistogram(2L, NEG_0_0)));
    }

    @Test
    public void testFilterLessThanHistogramNotChanged() {
        runTest(FilterAggregator.FilterOperation.LT,
                HistogramFilterAggregator.FilterIndeterminate.KEEP,
                NEG_512_0, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                        createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)));
    }

    @Test
    public void testFilterLessThanMaxNotChanged() {
        runTest(FilterAggregator.FilterOperation.LT,
                HistogramFilterAggregator.FilterIndeterminate.KEEP,
                NEG_1EN310, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, POS_0_0, POS_100_01)));
    }

    @Test
    public void testFilterLessThanOrEqualKeepThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.LTE, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, true,
                createGroup(createHistogram(1L, POS_100_0, POS_100_5),
                        createHistogram(2L, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterLessThanOrEqualKeepThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.LTE, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, false,
                createGroup(createHistogram(1L, NEG_99_5), createHistogram(2L, NEG_0_0)));
    }

    @Test
    public void testFilterLessThanOrEqualKeepThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.LTE, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, true,
                createGroup(createHistogram(1L, POS_100_0, POS_100_5),
                        createHistogram(2L, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterLessThanOrEqualKeepThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.LTE, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, false,
                createGroup(createHistogram(1L, NEG_99_5, NEG_100_0),
                        createHistogram(2L, NEG_0_0)));
    }

    @Test
    public void testFilterLessThanOrEqualDiscardThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.LTE, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, true,
                createGroup(createHistogram(1L, POS_100_5),
                        createHistogram(2L, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterLessThanOrEqualDiscardThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.LTE, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, false,
                createGroup(createHistogram(1L, NEG_99_5), createHistogram(2L, NEG_0_0)));
    }

    @Test
    public void testFilterLessThanOrEqualDiscardThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.LTE, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, true,
                createGroup(createHistogram(1L, POS_100_5),
                        createHistogram(2L, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterLessThanOrEqualDiscardThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.LTE, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, false,
                createGroup(createHistogram(1L, NEG_99_5), createHistogram(2L, NEG_0_0)));
    }

    @Test
    public void testFilterLessThanOrEqualHistogramNotChanged() {
        runTest(FilterAggregator.FilterOperation.LTE,
                HistogramFilterAggregator.FilterIndeterminate.KEEP,
                NEG_512_0, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)));
    }

    @Test
    public void testFilterLessThanOrEqualMaxNotChanged() {
        runTest(FilterAggregator.FilterOperation.LTE,
                HistogramFilterAggregator.FilterIndeterminate.KEEP,
                NEG_1EN310, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, POS_0_0, POS_100_01)));
    }

    @Test
    public void testFilterGreaterThanKeepThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.GT, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, true,
                createGroup(createHistogram(1L, POS_99_5, POS_100_0),
                        createHistogram(2L, POS_0_0)));
    }

    @Test
    public void testFilterGreaterThanKeepThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.GT, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, false,
                createGroup(createHistogram(1L, NEG_100_0, NEG_100_5),
                        createHistogram(2L, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterGreaterThanKeepThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.GT, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, true,
                createGroup(createHistogram(1L, POS_99_5, POS_100_0),
                        createHistogram(2L, POS_0_0)));
    }

    @Test
    public void testFilterGreaterThanKeepThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.GT, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, false,
                createGroup(createHistogram(1L, NEG_100_0, NEG_100_5),
                        createHistogram(2L, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterGreaterThanDiscardThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.GT, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, true,
                createGroup(createHistogram(1L, POS_99_5), createHistogram(2L, POS_0_0)));
    }

    @Test
    public void testFilterGreaterThanDiscardThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.GT, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, false,
                createGroup(createHistogram(1L, NEG_100_0, NEG_100_5),
                        createHistogram(2L, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterGreaterThanDiscardThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.GT, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, true,
                createGroup(createHistogram(1L, POS_99_5), createHistogram(2L, POS_0_0)));
    }

    @Test
    public void testFilterGreaterThanDiscardThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.GT, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, false,
                createGroup(createHistogram(1L, NEG_100_5),
                        createHistogram(2L, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterGreaterThanHistogramNotChanged() {
        runTest(FilterAggregator.FilterOperation.GTE,
                HistogramFilterAggregator.FilterIndeterminate.KEEP,
                POS_512_0, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)));
    }

    @Test
    public void testFilterGreaterThanMinNotChanged() {
        runTest(FilterAggregator.FilterOperation.GTE,
                HistogramFilterAggregator.FilterIndeterminate.KEEP,
                POS_1EN310, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, NEG_100_01,
                        HistogramFilterAggregator.binInclusiveBound(POS_0_0))));
    }

    @Test
    public void testFilterGreaterThanOrEqualKeepThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.GTE, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, true,
                createGroup(createHistogram(1L, POS_99_5), createHistogram(2L, POS_0_0)));
    }

    @Test
    public void testFilterGreaterThanOrEqualKeepThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.GTE, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, false,
                createGroup(createHistogram(1L, NEG_100_0, NEG_100_5),
                        createHistogram(2L, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterGreaterThanOrEqualKeepThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.GTE, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, true,
                createGroup(createHistogram(1L, POS_99_5, POS_100_0),
                        createHistogram(2L, POS_0_0)));
    }

    @Test
    public void testFilterGreaterThanOrEqualKeepThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.GTE, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, false,
                createGroup(createHistogram(1L, NEG_100_0, NEG_100_5),
                        createHistogram(2L, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterGreaterThanOrEqualDiscardThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.GTE, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, true,
                createGroup(createHistogram(1L, POS_99_5), createHistogram(2L, POS_0_0)));
    }

    @Test
    public void testFilterGreaterThanOrEqualDiscardThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.GTE, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, false,
                createGroup(createHistogram(1L, NEG_100_5),
                        createHistogram(2L, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterGreaterThanOrEqualDiscardThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.GTE, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, true,
                createGroup(createHistogram(1L, POS_99_5), createHistogram(2L, POS_0_0)));
    }

    @Test
    public void testFilterGreaterThanOrEqualDiscardThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.GTE, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, false,
                createGroup(createHistogram(1L, NEG_100_5),
                        createHistogram(2L, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterGreaterThanOrEqualHistogramNotChanged() {
        runTest(FilterAggregator.FilterOperation.GTE,
                HistogramFilterAggregator.FilterIndeterminate.KEEP,
                POS_512_0, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)));
    }

    @Test
    public void testFilterGreaterThanOrEqualMinNotChanged() {
        runTest(FilterAggregator.FilterOperation.GTE,
                HistogramFilterAggregator.FilterIndeterminate.KEEP,
                POS_1EN310,
                createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, NEG_100_01,
                        HistogramFilterAggregator.binInclusiveBound(POS_0_0))));
    }

    @Test
    public void testFilterEqualKeepThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.EQUAL, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, true,
                createGroup(createHistogram(1L, POS_99_5, POS_100_0, POS_100_5),
                        createHistogram(2L, POS_0_0, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterEqualKeepThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.EQUAL, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                true, false,
                createGroup(createHistogram(1L, NEG_99_5, NEG_100_0, NEG_100_5),
                        createHistogram(2L, NEG_0_0, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterEqualKeepThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.EQUAL, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, true,
                createGroup(createHistogram(1L, POS_99_5, POS_100_0, POS_100_5),
                        createHistogram(2L, POS_0_0, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterEqualKeepThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.EQUAL, HistogramFilterAggregator.FilterIndeterminate.KEEP,
                false, false,
                createGroup(createHistogram(1L, NEG_99_5, NEG_100_0, NEG_100_5),
                        createHistogram(2L, NEG_0_0, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterEqualDiscardThresholdAtBinBoundaryPositiveBins() {
        runTest(FilterAggregator.FilterOperation.EQUAL, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, true,
                createGroup(createHistogram(1L, POS_99_5, POS_100_5),
                        createHistogram(2L, POS_0_0, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterEqualDiscardThresholdAtBinBoundaryNegativeBins() {
        runTest(FilterAggregator.FilterOperation.EQUAL, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                true, false,
                createGroup(createHistogram(1L, NEG_99_5, NEG_100_5),
                        createHistogram(2L, NEG_0_0, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterEqualDiscardThresholdMiddleOfBinPositiveBins() {
        runTest(FilterAggregator.FilterOperation.EQUAL, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, true,
                createGroup(createHistogram(1L, POS_99_5, POS_100_5),
                        createHistogram(2L, POS_0_0, POS_512_0, POS_516_0)));
    }

    @Test
    public void testFilterEqualDiscardThresholdMiddleOfBinNegativeBins() {
        runTest(FilterAggregator.FilterOperation.EQUAL, HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                false, false,
                createGroup(createHistogram(1L, NEG_99_5, NEG_100_5),
                        createHistogram(2L, NEG_0_0, NEG_512_0, NEG_516_0)));
    }

    @Test
    public void testFilterEqualHistogramNotChangedTooHigh() {
        runTest(FilterAggregator.FilterOperation.EQUAL,
                HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                POS_512_0, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)));
    }

    @Test
    public void testFilterEqualHistogramNotChangedTooLow() {
        runTest(FilterAggregator.FilterOperation.EQUAL,
                HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                NEG_512_0, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)));
    }

    @Test
    public void testFilterEqualMinNotChanged() {
        runTest(FilterAggregator.FilterOperation.EQUAL,
                HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                POS_100_0, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, NEG_100_01,
                        HistogramFilterAggregator.binInclusiveBound(POS_0_0))));
    }

    @Test
    public void testFilterEqualMaxNotChanged() {
        runTest(FilterAggregator.FilterOperation.EQUAL,
                HistogramFilterAggregator.FilterIndeterminate.DISCARD,
                NEG_100_01, createGroup(createExactHistogram(1L, NEG_100_01, POS_0_0, POS_100_01)),
                createGroup(createExactHistogram(1L, POS_0_0, POS_100_01)));
    }
}
