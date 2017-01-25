/*******************************************************************************
 * Copyright 2016 Adobe Systems Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.adobe.cq.wcm.core.components.internal.servlets;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.servlet.MockRequestPathInfo;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.wcm.core.components.context.CoreComponentTestContext;
import com.adobe.cq.wcm.core.components.testing.MockAdapterFactory;
import com.day.cq.wcm.api.components.ComponentContext;
import com.day.cq.wcm.api.policies.ContentPolicy;
import com.day.cq.wcm.api.policies.ContentPolicyManager;
import com.day.cq.wcm.api.policies.ContentPolicyMapping;
import com.google.common.base.Function;
import io.wcm.testing.mock.aem.junit.AemContext;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class AdaptiveImageServletTest {

    @Rule
    public final AemContext aemContext = CoreComponentTestContext.createContext("/image", "/content");
    private ResourceResolver resourceResolver;
    private ContentPolicyManager contentPolicyManager;
    private AdaptiveImageServlet servlet;

    private static final String TEST_ROOT = "/content";
    private static final String PAGE = TEST_ROOT + "/test";
    private static final String IMAGE0_PATH = PAGE + "/jcr:content/root/image0";
    private static final String IMAGE1_PATH = PAGE + "/jcr:content/root/image1";
    private static final String IMAGE2_PATH = PAGE + "/jcr:content/root/image2";
    private static final String IMAGE_BINARY_NAME = "Adobe_Systems_logo_and_wordmark.svg.png";
    private static final String ASSET_PATH = "/content/dam/core/images/" + IMAGE_BINARY_NAME;
    private static final int ADAPTIVE_IMAGE_SERVLET_DEFAULT_RESIZE_WIDTH = 1280;



    @Before
    public void setUp() {
        aemContext.load().json("/image/test-conf.json", "/conf");
        aemContext.load().binaryFile("/image/" + IMAGE_BINARY_NAME, ASSET_PATH);
        aemContext.registerInjectActivateService(new MockAdapterFactory());
        resourceResolver = aemContext.resourceResolver();
        contentPolicyManager = mock(ContentPolicyManager.class);
        aemContext.registerAdapter(ResourceResolver.class, ContentPolicyManager.class,
                new Function<ResourceResolver, ContentPolicyManager>() {
                    @Nullable
                    @Override
                    public ContentPolicyManager apply(@Nullable ResourceResolver resolver) {
                        return contentPolicyManager;
                    }
                });
        servlet = new AdaptiveImageServlet();
    }

    @After
    public void tearDown() {
        resourceResolver = null;
        contentPolicyManager = null;
        servlet = null;
    }

    @Test
    public void testRequestWithWidthDesignAllowed() throws Exception {
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE0_PATH, "img.800", "png");
        ComponentContext componentContext = mock(ComponentContext.class);
        request.setAttribute(ComponentContext.CONTEXT_ATTR_NAME, componentContext);
        ContentPolicyMapping mapping = request.getResource().adaptTo(ContentPolicyMapping.class);
        ContentPolicy contentPolicy = mapping.getPolicy();
        when(contentPolicyManager.getPolicy(componentContext)).thenReturn(contentPolicy);
        activateServlet(servlet);
        servlet.doGet(request, response);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(response.getOutput());
        BufferedImage image = ImageIO.read(byteArrayInputStream);
        Dimension expectedDimension = new Dimension(800, 800);
        Dimension actualDimension = new Dimension(image.getWidth(), image.getHeight());
        assertEquals("Expected image rendered at requested size.", expectedDimension, actualDimension);
        assertEquals("Expected a PNG image.", "image/png", response.getContentType());
    }

    @Test
    public void testRequestWithWidthDesignNotAllowed() throws Exception {
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE0_PATH, "img.1000", "png");
        ComponentContext componentContext = mock(ComponentContext.class);
        request.setAttribute(ComponentContext.CONTEXT_ATTR_NAME, componentContext);
        ContentPolicyMapping mapping = request.getResource().adaptTo(ContentPolicyMapping.class);
        ContentPolicy contentPolicy = mapping.getPolicy();
        when(contentPolicyManager.getPolicy(componentContext)).thenReturn(contentPolicy);
        activateServlet(servlet);
        servlet.doGet(request, response);
        assertEquals("Expected a 404 response when the design does not allow the requested width to be rendered.",HttpServletResponse
                .SC_NOT_FOUND, response.getStatus());
        assertArrayEquals("Expected an empty response output.", new byte[0], response.getOutput());
    }

    @Test
    public void testRequestWithWidthNoDesign() throws Exception {
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE0_PATH, "img.800", "png");
        activateServlet(servlet);
        servlet.doGet(request, response);
        assertEquals("Expected a 404 response when the request contains width information but no content policy has been defined.",
                HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertArrayEquals("Expected an empty response output.", new byte[0], response.getOutput());
    }

    @Test
    public void testRequestNoWidthWithDesign() throws Exception {
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE0_PATH, "img", "png");
        ComponentContext componentContext = mock(ComponentContext.class);
        request.setAttribute(ComponentContext.CONTEXT_ATTR_NAME, componentContext);
        ContentPolicyMapping mapping = request.getResource().adaptTo(ContentPolicyMapping.class);
        ContentPolicy contentPolicy = mapping.getPolicy();
        when(contentPolicyManager.getPolicy(componentContext)).thenReturn(contentPolicy);
        activateServlet(servlet);
        servlet.doGet(request, response);
        verify(response).setContentType("image/png");
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(response.getOutput());
        BufferedImage image = ImageIO.read(byteArrayInputStream);
        Dimension expectedDimension = new Dimension(600, 600);
        Dimension actualDimension = new Dimension(image.getWidth(), image.getHeight());
        assertEquals("Expected image rendered using the first width defined in the content policy.", expectedDimension, actualDimension);
    }


    @Test
    public void testRequestNoWidthNoDesign() throws Exception {
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE0_PATH, "img", "png");
        activateServlet(servlet);
        servlet.doGet(request, response);
        verify(response).setContentType("image/png");
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(response.getOutput());
        BufferedImage image = ImageIO.read(byteArrayInputStream);
        Dimension expectedDimension = new Dimension(ADAPTIVE_IMAGE_SERVLET_DEFAULT_RESIZE_WIDTH, ADAPTIVE_IMAGE_SERVLET_DEFAULT_RESIZE_WIDTH);
        Dimension actualDimension = new Dimension(image.getWidth(), image.getHeight());
        assertEquals("Expected image rendered with the default resize configuration width.", expectedDimension, actualDimension);
    }

    @Test
    public void testWrongNumberOfSelectors() throws Exception {
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE0_PATH, "img.1.1", "png");
        activateServlet(servlet);
        servlet.doGet(request, response);
        assertEquals("Expected a 404 response when the request has more selectors than expected.",
                HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertArrayEquals("Expected an empty response output.", new byte[0], response.getOutput());
    }

    @Test
    public void testInvalidWidthSelector() throws Exception {
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE0_PATH, "img.full", "png");
        activateServlet(servlet);
        servlet.doGet(request, response);
        assertEquals("Expected a 404 response when the request has an invalid width selector.",
                HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertArrayEquals("Expected an empty response output.", new byte[0], response.getOutput());
    }

    @Test
    public void testWithInvalidDesignWidth() throws Exception {
        Logger logger = spy(LoggerFactory.getLogger("FakeLogger"));
        setFinalStatic(AdaptiveImageServlet.class.getDeclaredField("LOGGER"), logger);
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE1_PATH, "img.700", "png");
        ComponentContext componentContext = mock(ComponentContext.class);
        request.setAttribute(ComponentContext.CONTEXT_ATTR_NAME, componentContext);
        ContentPolicyMapping mapping = request.getResource().adaptTo(ContentPolicyMapping.class);
        ContentPolicy contentPolicy = mapping.getPolicy();
        when(contentPolicyManager.getPolicy(componentContext)).thenReturn(contentPolicy);
        activateServlet(servlet);
        servlet.doGet(request, response);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(response.getOutput());
        BufferedImage image = ImageIO.read(byteArrayInputStream);
        Dimension expectedDimension = new Dimension(700, 700);
        Dimension actualDimension = new Dimension(image.getWidth(), image.getHeight());
        assertEquals("Expected image rendered at requested size.", expectedDimension, actualDimension);
        assertEquals("Expected a PNG image.", "image/png", response.getContentType());
        verify(logger).warn(
                "One of the configured widths ({}) from the {} content policy is not a valid Integer.",
                "invalid",
                "/conf/we-retail/settings/wcm/policies/weretail/components/content/image/policy_1478854677327"
        );
    }

    @Test
    public void testWithInvalidFileReference() throws Exception {
        MockSlingHttpServletResponse response = spy(aemContext.response());
        MockSlingHttpServletRequest request = prepareRequest(IMAGE2_PATH, "img", "png");
        activateServlet(servlet);
        servlet.doGet(request, response);
        assertEquals("Expected a 404 response when the image does not have a valid file reference.",
                HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertArrayEquals("Expected an empty response output.", new byte[0], response.getOutput());
    }

    private MockSlingHttpServletRequest prepareRequest(String resourcePath, String selectorString, String extension) {
        MockSlingHttpServletRequest request = aemContext.request();
        Resource imageResource = resourceResolver.getResource(resourcePath);
        request.setResource(imageResource);
        aemContext.currentResource(imageResource);
        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) request.getRequestPathInfo();
        requestPathInfo.setSelectorString(selectorString);
        requestPathInfo.setExtension(extension);
        return request;
    }

    private void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);
        // remove final modifier from field
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

    private void activateServlet(AdaptiveImageServlet servlet) {
        servlet.activate(new AdaptiveImageServlet.Configuration() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }

            @Override
            public int defaultResizeWidth() {
                return ADAPTIVE_IMAGE_SERVLET_DEFAULT_RESIZE_WIDTH;
            }
        });
    }

}
