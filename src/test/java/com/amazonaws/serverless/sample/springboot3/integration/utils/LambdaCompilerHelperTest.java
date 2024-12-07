package com.amazonaws.serverless.sample.springboot3.integration.utils;
 
import org.junit.jupiter.api.Test; 
 
class LambdaCompilerHelperTest {

	@Test
	public void testGetTestJarBytes() {
		LambdaCompilerHelper.getTestJarBytes(null);
	}

}
