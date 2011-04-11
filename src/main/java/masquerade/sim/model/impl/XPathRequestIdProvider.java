package masquerade.sim.model.impl;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

/**
 * Extract unique request IDs from XML documents using an XPath
 */
public class XPathRequestIdProvider extends AbstractRequestIdProvider<Document> {

	private String xpath = "";
	
	public XPathRequestIdProvider(String name) {
		super(name);
	}

	/**
     * @return the xpath
     */
    public String getXpath() {
    	return xpath;
    }

	/**
     * @param xpath the xpath to set
     */
    public void setXpath(String xpath) {
    	this.xpath = xpath;
    }

	@Override
    public String getUniqueId(Document request) {
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		try {
			XPathExpression expr = xpath.compile(this.xpath);
			return (String) expr.evaluate(request, XPathConstants.STRING);
		} catch (XPathExpressionException e) {
			throw new IllegalArgumentException("Failed to evaluate XPath on request", e);
		}
    }

	@Override
    public String toString() {
	    return "XPathRequestIdProvider " + xpath;
    }	
}
