/*
 * Copyright 2010-2011 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 * 
 *  http://aws.amazon.com/apache2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.ec2.model.transform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.Request;
import com.amazonaws.DefaultRequest;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.transform.Marshaller;
import com.amazonaws.util.StringUtils;

/**
 * Describe Spot Price History Request Marshaller
 */
public class DescribeSpotPriceHistoryRequestMarshaller implements Marshaller<Request<DescribeSpotPriceHistoryRequest>, DescribeSpotPriceHistoryRequest> {

    public Request<DescribeSpotPriceHistoryRequest> marshall(DescribeSpotPriceHistoryRequest describeSpotPriceHistoryRequest) {
        Request<DescribeSpotPriceHistoryRequest> request = new DefaultRequest<DescribeSpotPriceHistoryRequest>(describeSpotPriceHistoryRequest, "AmazonEC2");
        request.addParameter("Action", "DescribeSpotPriceHistory");
        request.addParameter("Version", "2011-05-15");
        if (describeSpotPriceHistoryRequest != null) {
            if (describeSpotPriceHistoryRequest.getStartTime() != null) {
                request.addParameter("StartTime", StringUtils.fromDate(describeSpotPriceHistoryRequest.getStartTime()));
            }
        }
        if (describeSpotPriceHistoryRequest != null) {
            if (describeSpotPriceHistoryRequest.getEndTime() != null) {
                request.addParameter("EndTime", StringUtils.fromDate(describeSpotPriceHistoryRequest.getEndTime()));
            }
        }
        if (describeSpotPriceHistoryRequest != null) {
            java.util.List<String> instanceTypesList = describeSpotPriceHistoryRequest.getInstanceTypes();
            int instanceTypesListIndex = 1;

            for (String instanceTypesListValue : instanceTypesList) {
                if (instanceTypesListValue != null) {
                    request.addParameter("InstanceType." + instanceTypesListIndex, StringUtils.fromString(instanceTypesListValue));
                }
                instanceTypesListIndex++;
            }
        }
        if (describeSpotPriceHistoryRequest != null) {
            java.util.List<String> productDescriptionsList = describeSpotPriceHistoryRequest.getProductDescriptions();
            int productDescriptionsListIndex = 1;

            for (String productDescriptionsListValue : productDescriptionsList) {
                if (productDescriptionsListValue != null) {
                    request.addParameter("ProductDescription." + productDescriptionsListIndex, StringUtils.fromString(productDescriptionsListValue));
                }
                productDescriptionsListIndex++;
            }
        }

        if (describeSpotPriceHistoryRequest != null) {
            java.util.List<Filter> filtersList = describeSpotPriceHistoryRequest.getFilters();
            int filtersListIndex = 1;
            for (Filter filtersListValue : filtersList) {
                if (filtersListValue != null) {
                    if (filtersListValue.getName() != null) {
                        request.addParameter("Filter." + filtersListIndex + ".Name", StringUtils.fromString(filtersListValue.getName()));
                    }
                }
                if (filtersListValue != null) {
                    java.util.List<String> valuesList = filtersListValue.getValues();
                    int valuesListIndex = 1;

                    for (String valuesListValue : valuesList) {
                        if (valuesListValue != null) {
                            request.addParameter("Filter." + filtersListIndex + ".Value." + valuesListIndex, StringUtils.fromString(valuesListValue));
                        }
                        valuesListIndex++;
                    }
                }

                filtersListIndex++;
            }
        }

        if (describeSpotPriceHistoryRequest != null) {
            if (describeSpotPriceHistoryRequest.getAvailabilityZone() != null) {
                request.addParameter("AvailabilityZone", StringUtils.fromString(describeSpotPriceHistoryRequest.getAvailabilityZone()));
            }
        }
        if (describeSpotPriceHistoryRequest != null) {
            if (describeSpotPriceHistoryRequest.getMaxResults() != null) {
                request.addParameter("MaxResults", StringUtils.fromInteger(describeSpotPriceHistoryRequest.getMaxResults()));
            }
        }
        if (describeSpotPriceHistoryRequest != null) {
            if (describeSpotPriceHistoryRequest.getNextToken() != null) {
                request.addParameter("NextToken", StringUtils.fromString(describeSpotPriceHistoryRequest.getNextToken()));
            }
        }
        return request;
    }
}
