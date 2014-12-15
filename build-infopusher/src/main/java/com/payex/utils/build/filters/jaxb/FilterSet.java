package com.payex.utils.build.filters.jaxb;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "FilterSet")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class FilterSet {
	List<Filter> filters = new ArrayList<Filter>();

	@XmlElement(name="Filter")
	@XmlElementWrapper(name="Filters")
	public List<Filter> getFilters() {
		return filters;
	}

	public void setFilters(List<Filter> filters) {
		this.filters = filters;
	}

}
