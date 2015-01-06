package com.payex.utils.build;

import java.net.URL;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

import org.datacontract.schemas._2004._07.buildwatch.PushFinishedBuildsRequest;
import org.tempuri.DataService;
import org.tempuri.IDataService;

import com.sun.xml.ws.client.BindingProviderProperties;

public class BuildResultPusher {
	private String endpointUrl;
	private IDataService publisherService;

	
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
		PushFinishedBuildsRequest r = req;
		try {
			publisherService.pushFinishedBuilds(r);
		} catch (javax.xml.ws.soap.SOAPFaultException ex) {
			throw new RuntimeException(ex);
		}
	}

	private IDataService initializeWsClient() {
		URL wsdlLocation = BuildResultPusher.class.getClassLoader()
         .getResource("META-INF/wsdl/DataService.wsdl");
		 QName serviceDef = new QName("http://tempuri.org/", "DataService");
		 DataService ma = new DataService(wsdlLocation, serviceDef);
		 
		IDataService ds = ma.getPort(IDataService.class);
		BindingProvider bp = (BindingProvider) ds;
		setBindingParameters(bp, 0, 0, getServiceEndpointUrl());
		return ds;
	}

	private String getServiceEndpointUrl() {
		return endpointUrl;
	}

	public void init() {
		publisherService = initializeWsClient();
	}

}
