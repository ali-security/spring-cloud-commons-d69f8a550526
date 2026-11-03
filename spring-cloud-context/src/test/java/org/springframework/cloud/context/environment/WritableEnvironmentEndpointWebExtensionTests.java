/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.context.environment;

import java.util.Collections;

import org.junit.Test;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.context.environment.WritableEnvironmentEndpointWebExtension.VALID_KEYS_REGEX_PROPERTY;

public class WritableEnvironmentEndpointWebExtensionTests {

	private final ApplicationEventPublisher publisher = mock(
			ApplicationEventPublisher.class);

	private WritableEnvironmentEndpointWebExtension extension(
			MockEnvironment environment) {
		EnvironmentManager environmentManager = new EnvironmentManager(environment);
		environmentManager.setApplicationEventPublisher(this.publisher);
		return new WritableEnvironmentEndpointWebExtension(
				new WritableEnvironmentEndpoint(environment), environmentManager);
	}

	@Test
	public void writeRejectedWhenNoValidKeysRegexConfigured() {
		MockEnvironment environment = new MockEnvironment();
		WritableEnvironmentEndpointWebExtension extension = extension(environment);

		thenThrownBy(() -> extension.write("spring.datasource.url", "jdbc:evil"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid key spring.datasource.url");
		then(environment.getProperty("spring.datasource.url")).isNull();
		verify(this.publisher, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void writeRejectedWhenValidKeysRegexIsBlank() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty(VALID_KEYS_REGEX_PROPERTY, " ");
		WritableEnvironmentEndpointWebExtension extension = extension(environment);

		thenThrownBy(() -> extension.write("message", "Foo"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid key message");
		then(environment.getProperty("message")).isNull();
		verify(this.publisher, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void writeRejectedWhenKeyDoesNotMatchValidKeysRegex() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty(VALID_KEYS_REGEX_PROPERTY, "message");
		WritableEnvironmentEndpointWebExtension extension = extension(environment);

		thenThrownBy(() -> extension.write("xmessage", "Foo"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid key xmessage");
		// the whole key must match, not only a part of it
		thenThrownBy(() -> extension.write("messagex", "Foo"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid key messagex");
		// the allow list itself cannot be widened through the endpoint
		thenThrownBy(() -> extension.write(VALID_KEYS_REGEX_PROPERTY, ".*"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid key " + VALID_KEYS_REGEX_PROPERTY);
		then(environment.getProperty("xmessage")).isNull();
		then(environment.getProperty("messagex")).isNull();
		then(environment.getProperty(VALID_KEYS_REGEX_PROPERTY)).isEqualTo("message");
		verify(this.publisher, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void writeAllowedWhenKeyMatchesValidKeysRegex() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty(VALID_KEYS_REGEX_PROPERTY, "message");
		WritableEnvironmentEndpointWebExtension extension = extension(environment);

		then(extension.write("message", "Foo"))
				.isEqualTo(Collections.singletonMap("message", "Foo"));
		then(environment.getProperty("message")).isEqualTo("Foo");
		verify(this.publisher, times(1)).publishEvent(any(ApplicationEvent.class));
	}

}
