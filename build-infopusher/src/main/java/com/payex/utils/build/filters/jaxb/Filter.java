package com.payex.utils.build.filters.jaxb;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;

@XmlType(name="Filter")
@XmlAccessorType(XmlAccessType.FIELD)
public class Filter {
	@XmlAttribute
	private String name;
	@XmlElement(name="Test")
	@XmlElementWrapper(name="Tests")
	private List<Test> tests;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<Test> getTests() {
		return tests;
	}
	public void setTests(List<Test> tests) {
		this.tests = tests;
	}
	
}
