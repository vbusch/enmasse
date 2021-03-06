/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.selenium.resources;

import io.enmasse.systemtest.model.address.AddressStatus;
import io.enmasse.systemtest.model.address.AddressType;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class AddressWebItem extends WebItem implements Comparable<AddressWebItem> {
    private WebElement checkBox;
    private String address;
    private String plan;
    private WebElement clientsRoute;
    private String timestamp;
    private double messagesIn;
    private double messagesOut;
    private int messagesStored;
    private int senders;
    private int receivers;
    private int partitions;
    private String type;
    private WebElement actionDropDown;
    private String statusString;
    private String status;

    public AddressWebItem(WebElement item) {
        this.webItem = item;
        this.checkBox = webItem.findElement(By.xpath("./td[@data-key='0']")).findElement(By.tagName("input"));
        this.address = parseName(webItem.findElement(By.xpath("./td[@data-label='Name']")));
        this.clientsRoute = parseRoute(webItem.findElement(By.xpath("./td[@data-label='Name']")));
        try {
            this.plan = webItem.findElement(By.xpath("./td[@data-label='Type/Plan']")).getText().toLowerCase().substring(2);
        } catch (Exception ex) {
            this.plan = "";
        }
        this.type = webItem.findElement(By.xpath("./td[@data-label='Type/Plan']")).getText().substring(0, 1);
        this.status = webItem.findElement(By.xpath("./td[@data-label='Status']")).getText().replace(" ", "");
        try {
            this.timestamp = webItem.findElement(By.xpath("./td[@data-label='Time created']")).getText();
            this.messagesIn =  defaultDouble(webItem.findElement(By.xpath("./td[@data-label='Message In']")).getText());
            this.messagesOut =  defaultDouble(webItem.findElement(By.xpath("./td[@data-label='Message Out']")).getText());

            this.messagesStored = defaultInt(webItem.findElement(By.xpath("./td[@data-label='Stored Messages']")).getText());
            this.senders =  defaultInt(webItem.findElement(By.xpath("./td[@data-label='Senders']")).getText());
            this.receivers =  defaultInt(webItem.findElement(By.xpath("./td[@data-label='Receivers']")).getText());

            this.partitions =  defaultInt(webItem.findElement(By.xpath("./td[@data-label='Partitions']")).getText());
        } catch (Exception ex) {
            this.statusString = webItem.findElement(By.xpath("./td[@data-label='Time created']")).getText();
        }
        this.actionDropDown = webItem.findElement(By.className("pf-c-dropdown"));
    }

    public WebElement getCheckBox() {
        return checkBox;
    }

    public String getAddress() {
        return address;
    }

    public WebElement getClientsRoute() {
        return clientsRoute;
    }

    public String getPlan() {
        return plan;
    }

    public double getMessagesIn() {
        return messagesIn;
    }

    public double getMessagesOut() {
        return messagesOut;
    }

    public int getMessagesStored() {
        return messagesStored;
    }

    public int getSendersCount() {
        return senders;
    }

    public int getReceiversCount() {
        return receivers;
    }

    public int getPartitions() {
        return partitions;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public AddressStatus getStatus() {
        switch (status) {
            case "Active":
                return AddressStatus.READY;
            case "Configuring":
            case "Pending":
                return AddressStatus.PENDING;
            default:
                return AddressStatus.ERROR;
        }
    }

    public String getType() {
        switch (this.type) {
            case "Q":
                return AddressType.QUEUE.toString();
            case "T":
                return AddressType.TOPIC.toString();
            case "A":
                return AddressType.ANYCAST.toString();
            case "M":
                return AddressType.MULTICAST.toString();
            case "S":
                return AddressType.SUBSCRIPTION.toString();
            default:
                return "";
        }
    }

    public String getStatusString() {
        return statusString;
    }

    public WebElement getActionDropDown() {
        return actionDropDown;
    }

    public WebElement getEditMenuItem() {
        return getActionDropDown().findElement(By.id("edit-address"));
    }

    public WebElement getDeleteMenuItem() {
        return getActionDropDown().findElement(By.id("delete-address"));
    }

    public WebElement getPurgeMenuItem() {
        return getActionDropDown().findElement(By.id("purge-address"));
    }

    private String parseName(WebElement elem) {
        try {
            return elem.findElement(By.tagName("a")).getText();
        } catch (Exception ex) {
            return elem.findElements(By.tagName("p")).get(0).getText();
        }
    }

    private WebElement parseRoute(WebElement elem) {
        try {
            return elem.findElement(By.tagName("a"));
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("name: %s, type: %s, plan: %s, timestamp: %s, messagesIn: %f, messagesOut: %f, stored: %d, senders: %d, receivers: %d, partitions: %d, status: %s, statusMessage: %s",
                this.address,
                getType(),
                this.plan,
                this.timestamp,
                this.messagesIn,
                this.messagesOut,
                this.messagesStored,
                this.senders,
                this.receivers,
                this.partitions,
                this.status,
                this.statusString);
    }

    @Override
    public int compareTo(AddressWebItem o) {
        return address.compareTo(o.address);
    }
}
