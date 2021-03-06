/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.test.web.servlet.samples.standalone.resultmatchers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.Person;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.WebRequest;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

/**
 * Examples of expectations on response header values.
 *
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @author Brian Clozel
 */
public class HeaderAssertionTests {

	private static final String ERROR_MESSAGE = "Should have thrown an AssertionError";


	private String now;

	private String minuteAgo;

	private String secondLater;

	private MockMvc mockMvc;

	private final long currentTime = System.currentTimeMillis();


	@Before
	public void setup() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		this.now = dateFormat.format(new Date(this.currentTime));
		this.minuteAgo = dateFormat.format(new Date(this.currentTime - (1000 * 60)));
		this.secondLater = dateFormat.format(new Date(this.currentTime + 1000));

		PersonController controller = new PersonController();
		controller.setStubTimestamp(this.currentTime);
		this.mockMvc = standaloneSetup(controller).build();
	}


	@Test
	public void stringWithCorrectResponseHeaderValue() throws Exception {
		this.mockMvc.perform(get("/persons/1").header(IF_MODIFIED_SINCE, minuteAgo))
				.andExpect(header().string(LAST_MODIFIED, now));
	}

	@Test
	public void stringWithMatcherAndCorrectResponseHeaderValue() throws Exception {
		this.mockMvc.perform(get("/persons/1").header(IF_MODIFIED_SINCE, minuteAgo))
				.andExpect(header().string(LAST_MODIFIED, equalTo(now)));
	}

	@Test
	public void multiStringHeaderValue() throws Exception {
		this.mockMvc.perform(get("/persons/1")).andExpect(header().stringValues(VARY, "foo", "bar"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void multiStringHeaderValueWithMatchers() throws Exception {
		this.mockMvc.perform(get("/persons/1"))
				.andExpect(header().stringValues(VARY, hasItems(containsString("foo"), startsWith("bar"))));
	}

	@Test
	public void dateValueWithCorrectResponseHeaderValue() throws Exception {
		this.mockMvc.perform(get("/persons/1").header(IF_MODIFIED_SINCE, minuteAgo))
				.andExpect(header().dateValue(LAST_MODIFIED, this.currentTime));
	}

	@Test
	public void longValueWithCorrectResponseHeaderValue() throws Exception {
		this.mockMvc.perform(get("/persons/1"))
				.andExpect(header().longValue("X-Rate-Limiting", 42));
	}

	@Test
	public void stringWithMissingResponseHeader() throws Exception {
		this.mockMvc.perform(get("/persons/1").header(IF_MODIFIED_SINCE, now))
				.andExpect(status().isNotModified())
				.andExpect(header().stringValues("X-Custom-Header"));
	}

	@Test
	public void stringWithMatcherAndMissingResponseHeader() throws Exception {
		this.mockMvc.perform(get("/persons/1").header(IF_MODIFIED_SINCE, now))
				.andExpect(status().isNotModified())
				.andExpect(header().string("X-Custom-Header", nullValue()));
	}

	@Test
	public void longValueWithMissingResponseHeader() throws Exception {
		try {
			this.mockMvc.perform(get("/persons/1").header(IF_MODIFIED_SINCE, now))
					.andExpect(status().isNotModified())
					.andExpect(header().longValue("X-Custom-Header", 99L));

			fail(ERROR_MESSAGE);
		}
		catch (AssertionError err) {
			if (ERROR_MESSAGE.equals(err.getMessage())) {
				throw err;
			}
			assertEquals("Response does not contain header 'X-Custom-Header'", err.getMessage());
		}
	}

	@Test  // SPR-10771
	public void doesNotExist() throws Exception {
		this.mockMvc.perform(get("/persons/1")).andExpect(header().doesNotExist("X-Custom-Header"));
	}

	@Test(expected = AssertionError.class)  // SPR-10771
	public void doesNotExistFail() throws Exception {
		this.mockMvc.perform(get("/persons/1")).andExpect(header().doesNotExist(LAST_MODIFIED));
	}

	@Test
	public void doesExist() throws Exception {
		this.mockMvc.perform(get("/persons/1")).andExpect(header().doesExist(LAST_MODIFIED));
	}

	@Test(expected = AssertionError.class)
	public void doesExistFail() throws Exception {
		this.mockMvc.perform(get("/persons/1")).andExpect(header().doesExist("X-Custom-Header"));
	}

	@Test
	public void stringWithIncorrectResponseHeaderValue() throws Exception {
		assertIncorrectResponseHeader(header().string(LAST_MODIFIED, secondLater), secondLater);
	}

	@Test
	public void stringWithMatcherAndIncorrectResponseHeaderValue() throws Exception {
		assertIncorrectResponseHeader(header().string(LAST_MODIFIED, equalTo(secondLater)), secondLater);
	}

	@Test
	public void dateValueWithIncorrectResponseHeaderValue() throws Exception {
		long unexpected = this.currentTime + 1000;
		assertIncorrectResponseHeader(header().dateValue(LAST_MODIFIED, unexpected), secondLater);
	}

	@Test(expected = AssertionError.class)
	public void longValueWithIncorrectResponseHeaderValue() throws Exception {
		this.mockMvc.perform(get("/persons/1")).andExpect(header().longValue("X-Rate-Limiting", 1));
	}


	private void assertIncorrectResponseHeader(ResultMatcher matcher, String unexpected) throws Exception {
		try {
			this.mockMvc.perform(get("/persons/1")
					.header(IF_MODIFIED_SINCE, minuteAgo))
					.andExpect(matcher);

			fail(ERROR_MESSAGE);
		}
		catch (AssertionError err) {
			if (ERROR_MESSAGE.equals(err.getMessage())) {
				throw err;
			}
			// SPR-10659: ensure header name is in the message
			// Unfortunately, we can't control formatting from JUnit or Hamcrest.
			assertMessageContains(err, "Response header '" + LAST_MODIFIED + "'");
			assertMessageContains(err, unexpected);
			assertMessageContains(err, now);
		}
	}

	private void assertMessageContains(AssertionError error, String expected) {
		String message = error.getMessage();
		assertTrue("Failure message should contain: " + expected, message.contains(expected));
	}


	@Controller
	private static class PersonController {

		private long timestamp;

		public void setStubTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}

		@RequestMapping("/persons/{id}")
		public ResponseEntity<Person> showEntity(@PathVariable long id, WebRequest request) {
			return ResponseEntity
					.ok()
					.lastModified(calculateLastModified(id))
					.header("X-Rate-Limiting", "42")
					.header("Vary", "foo", "bar")
					.body(new Person("Jason"));
		}

		private long calculateLastModified(long id) {
			return this.timestamp;
		}
	}

}
