package com.payex.utils.build.filters;

import java.io.File;
import java.util.ArrayList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;

import com.payex.utils.build.filters.jaxb.Action;
import com.payex.utils.build.filters.jaxb.Filter;
import com.payex.utils.build.filters.jaxb.FilterSet;
import com.payex.utils.build.filters.jaxb.Test;

public class FilterReader {

	public static void main(String[] args) throws JAXBException {
		new FilterReader().readFilters("filters.xml");
		writeXml();
	}

	public FilterSet readFilters(String filterFilePath) {
		JAXBContext jc;
		try {
			jc = JAXBContext.newInstance(FilterSet.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			FilterSet ret = (FilterSet) unmarshaller.unmarshal(new File(filterFilePath));
			return ret;
		} catch (JAXBException e) {
			throw new RuntimeException("Error reading filter config", e);
		}
	}
	private static void writeXml() throws JAXBException, PropertyException {
		JAXBContext jc = JAXBContext.newInstance(FilterSet.class);

        Marshaller m = jc.createMarshaller();
        FilterSet o = new FilterSet();
        Filter f = new Filter();
        f.setName("MPS");
        ArrayList<Test> tl = new ArrayList<Test>();
        Test t = new Test();
        t.setRegex("^kazkas");
        t.setAction(Action.notify);
		tl.add(t);
		f.setTests(tl);
		o.getFilters().add(f);
		
		f = new Filter();
        f.setName("MPS2");
        o.getFilters().add(f);
        
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		m.marshal(o, System.out);
	}

}
