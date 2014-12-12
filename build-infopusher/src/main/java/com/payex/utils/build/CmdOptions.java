package com.payex.utils.build;

import org.kohsuke.args4j.Option;

public class CmdOptions {
	@Option(name="-je",usage="Jenkins URL")
	String jenkinsUrl;
	@Option(name="-ju",usage="Jenkins username")
	String jenkinsUser;
	@Option(name="-jp",usage="Jenkins password")
	String jenkinsPass;
	@Option(name="-pe",usage="Publisher URL")
	String publisherUrl;
	@Option(name="-i",usage="Refresh interval")
	Long refreshInterval;
}
