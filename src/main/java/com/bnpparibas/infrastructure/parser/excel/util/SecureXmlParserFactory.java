package com.bnpparibas.infrastructure.parser.excel.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecureXmlParserFactory {

    /**
     * Create a secure SAXParserFactory with XXE protection
     *
     * @return Securely configured SAXParserFactory
     * @throws ParserConfigurationException if configuration fails
     * @throws SAXException if SAX configuration fails
     */
    public static SAXParserFactory createSecureSAXParserFactory()
            throws ParserConfigurationException, SAXException {

        SAXParserFactory factory = SAXParserFactory.newInstance();

        // Disable external DTDs
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        // Disable external entities
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        // Disable external DTD loading
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        // Enable secure processing
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // Disable XInclude
        factory.setXIncludeAware(false);

        log.debug("Created secure SAXParserFactory");

        return factory;
    }

    /**
     * Create a secure DocumentBuilderFactory with XXE protection
     *
     * @return Securely configured DocumentBuilderFactory
     * @throws ParserConfigurationException if configuration fails
     */
    public static DocumentBuilderFactory createSecureDocumentBuilderFactory()
            throws ParserConfigurationException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        // Disable external DTDs
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        // Disable external entities
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        // Disable external DTD loading
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        // Enable secure processing
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // Disable XInclude
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        log.debug("Created secure DocumentBuilderFactory");

        return factory;
    }

    /**
     * Create a secure XMLInputFactory with XXE protection
     *
     * @return Securely configured XMLInputFactory
     */
    public static XMLInputFactory createSecureXMLInputFactory() {

        XMLInputFactory factory = XMLInputFactory.newInstance();

        // Disable external DTDs
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        // Disable external entities
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        log.debug("Created secure XMLInputFactory");

        return factory;
    }
}
