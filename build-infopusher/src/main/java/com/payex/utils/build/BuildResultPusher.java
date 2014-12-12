package com.payex.utils.build;

import java.util.Map;

import javax.xml.ws.BindingProvider;

import org.datacontract.schemas._2004._07.buildwatch.PushFinishedBuildsRequest;
import org.tempuri.DataService;
import org.tempuri.IDataService;

import com.sun.xml.ws.client.BindingProviderProperties;

public class BuildResultPusher {
	private String endpointUrl;

	
	public BuildResultPusher(String endpointUrl) {
		super();
		this.endpointUrl = endpointUrl;
	}

	protected void setBindingParameters(BindingProvider bp, int connectTimeout,
			int requestTimeout, String endpointUrl) {
		final Map<String, Object> reqCtx = bp.getRequestContext();
		reqCtx.put(BindingProviderProperties.CONNECT_TIMEOUT, connectTimeout);
		reqCtx.put(BindingProviderProperties.REQUEST_TIMEOUT, requestTimeout);
		reqCtx.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
				getServiceEndpointUrl());
	}

	public void pushResults(PushFinishedBuildsRequest req) {
		IDataService ds = new DataService().getPort(IDataService.class);
		BindingProvider bp = (BindingProvider) ds;
		setBindingParameters(bp, 0, 0, getServiceEndpointUrl());

		PushFinishedBuildsRequest r = req;
		try {
			ds.pushFinishedBuilds(r);
		} catch (javax.xml.ws.soap.SOAPFaultException ex) {
			throw new RuntimeException(ex);
		}
	}

	private String getServiceEndpointUrl() {
		return endpointUrl;
	}

}
