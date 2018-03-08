package io.ebean.querybean.generator;


import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessingContextTest {

	@Test
	public void typeDefn() {

		assertThat(ProcessingContext.typeDef("java.lang.String")).isEqualTo("java.lang.String");
		assertThat(ProcessingContext.typeDef("(@javax.validation.constraints.Size(min=1, max=10) :: java.lang.String)")).isEqualTo("java.lang.String");
	}
}