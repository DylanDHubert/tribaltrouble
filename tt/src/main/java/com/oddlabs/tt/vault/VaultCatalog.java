package com.oddlabs.tt.vault;

import com.oddlabs.util.Utils;
import org.jspecify.annotations.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * LOADS THE SPRITE CATALOG FROM geometry.xml (AUTHORING INDEX; RUNTIME STILL USES .binsprite).
 */
public final class VaultCatalog {
    private static final Logger logger = Logger.getLogger(VaultCatalog.class.getName());
    private static final String GEOMETRY_XML = "/geometry/geometry.xml";

    private final @NonNull List<@NonNull VaultEntry> entries;

    private VaultCatalog(@NonNull List<@NonNull VaultEntry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public static @NonNull VaultCatalog load() {
        URL url = Utils.makeURL(GEOMETRY_XML);
        try (InputStream in = url.openStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(in);
            Element root = document.getDocumentElement();
            List<VaultEntry> list = new ArrayList<>();
            NodeList groups = root.getChildNodes();
            for (int i = 0; i < groups.getLength(); i++) {
                Node groupNode = groups.item(i);
                if (groupNode.getNodeType() != Node.ELEMENT_NODE || !"group".equals(groupNode.getNodeName()))
                    continue;
                Element group = (Element) groupNode;
                String groupName = group.getAttribute("name");
                NodeList children = group.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child.getNodeType() != Node.ELEMENT_NODE || !"sprite".equals(child.getNodeName()))
                        continue;
                    Element sprite = (Element) child;
                    String name = sprite.getAttribute("name");
                    float scale = 1f;
                    String scaleAttr = sprite.getAttribute("scale");
                    if (scaleAttr != null && !scaleAttr.isEmpty())
                        scale = Float.parseFloat(scaleAttr);
                    boolean modulate = name.contains("plant");
                    list.add(new VaultEntry(groupName, name, scale, modulate));
                }
            }
            logger.info("Vault catalog loaded: " + list.size() + " sprites from " + GEOMETRY_XML);
            return new VaultCatalog(list);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load vault catalog from " + GEOMETRY_XML, e);
        }
    }

    public @NonNull List<@NonNull VaultEntry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public @NonNull VaultEntry get(int index) {
        return entries.get(index);
    }
}
