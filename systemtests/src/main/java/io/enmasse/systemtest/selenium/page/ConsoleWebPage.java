/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.selenium.page;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.AuthenticationServiceType;
import io.enmasse.address.model.EndpointSpec;
import io.enmasse.address.model.ExposeType;
import io.enmasse.systemtest.UserCredentials;
import io.enmasse.systemtest.certs.CertProvider;
import io.enmasse.systemtest.logs.CustomLogger;
import io.enmasse.systemtest.model.address.AddressType;
import io.enmasse.systemtest.model.addressspace.AddressSpaceType;
import io.enmasse.systemtest.platform.KubeCMDClient;
import io.enmasse.systemtest.selenium.SeleniumProvider;
import io.enmasse.systemtest.selenium.resources.AddressSpaceWebItem;
import io.enmasse.systemtest.selenium.resources.AddressWebItem;
import io.enmasse.systemtest.selenium.resources.ClientWebItem;
import io.enmasse.systemtest.selenium.resources.ConnectionWebItem;
import io.enmasse.systemtest.selenium.resources.EndpointItem;
import io.enmasse.systemtest.selenium.resources.FilterType;
import io.enmasse.systemtest.selenium.resources.SortType;
import io.enmasse.systemtest.time.TimeoutBudget;
import io.enmasse.systemtest.utils.AddressSpaceUtils;
import io.enmasse.systemtest.utils.AddressUtils;
import org.apache.commons.codec.binary.Base64;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class ConsoleWebPage implements IWebPage {

    private static final Logger log = CustomLogger.getLogger();
    private static final By ADDRESS_LIST_XPATH = By.xpath("//table[@aria-label='Address List']");
    private static final By CONNECTION_LIST_XPATH = By.xpath("//table[@aria-label='connection list']");
    private static final By ENDPOINT_LIST_XPATH = By.xpath("//table[@aria-label='Endpoint List']");
    private static final By NOT_FOUND_STATE_XPATH = By.className("pf-c-empty-state");

    SeleniumProvider selenium;
    String ocRoute;
    UserCredentials credentials;
    OpenshiftLoginWebPage loginPage;

    public ConsoleWebPage(SeleniumProvider selenium, String ocRoute, UserCredentials credentials) {
        this.selenium = selenium;
        this.ocRoute = ocRoute;
        this.credentials = credentials;
        this.loginPage = new OpenshiftLoginWebPage(selenium);
    }

    //================================================================================================
    // Getters and finders of elements and data
    //================================================================================================
    private WebElement getLoginButton() {
        return selenium.getDriver().findElement(By.xpath("//button[contains(text(), 'Log in with OpenShift')]"));
    }

    public WebElement getEmptyAddSpace() {
        return selenium.getDriver().findElement(By.id("empty-ad-space"));
    }

    private WebElement getAddressTab() {
        return getContentElem().findElement(By.id("ad-space-nav-addresses"));
    }

    private WebElement getConnectionTab() {
        return getContentElem().findElement(By.id("ad-space-nav-connections"));
    }

    private WebElement getEndpointTab() {
        return getContentElem().findElement(By.id("ad-space-nav-endpoints"));
    }

    // Table selectors
    private WebElement getContentElem() {
        return selenium.getDriver().findElement(By.id("main-container"));
    }

    private WebElement getCreateButtonTop() throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("al-filter-overflow-button")));
    }

    private WebElement getAddressSpaceTable() {
        return selenium.getDriver().findElement(By.xpath("//table[@aria-label='address space list']"));
    }

    private WebElement getAddressSpaceList() {
        return getAddressSpaceTable().findElement(By.tagName("tbody"));
    }

    private WebElement getAddressTable() {
        return selenium.getDriver().findElement(ADDRESS_LIST_XPATH);
    }

    private WebElement getTableAddressHeader() {
        return getAddressTable().findElement(By.id("address-list-table-bodheader"));
    }

    private WebElement getTableAddressList() {
        return getAddressTable().findElement(By.tagName("tbody"));
    }

    private WebElement getEndpointsTable() {
        return selenium.getDriver().findElement(ENDPOINT_LIST_XPATH);
    }

    private WebElement getEndpointsList() {
        return getEndpointsTable().findElement(By.tagName("tbody"));
    }

    private WebElement getConnectionTable() {
        return selenium.getDriver().findElement(CONNECTION_LIST_XPATH);
    }

    private WebElement getTableConnectionHeader() {
        return getConnectionTable().findElement(By.id("connectionlist-table-header"));
    }

    private WebElement getTableClientsList() {
        return getClientsTable().findElement(By.tagName("tbody"));
    }

    private WebElement getClientsTable() {
        return selenium.getDriver().findElement(By.xpath("//table[@aria-label='client list']"));
    }

    private WebElement getTableConnectionList() {
        return getConnectionTable().findElement(By.tagName("tbody"));
    }

    private WebElement getAddressSpacesTableDropDown() {
        return getContentElem().findElement(By.id("al-filter-overflow-kebab"));
    }

    private WebElement getAddressesTableDropDown() {
        return getContentElem().findElement(By.id("al-filter-overflow-kebab"));
    }

    private WebElement getConnectionTableDropDown() {
        return getContentElem().findElement(By.id("cl-filter-overflow-kebab"));
    }

    private WebElement getAddressSpacesDeleteAllButton() {
        return getAddressSpacesTableDropDown().findElement(By.xpath("//button[contains(text(), 'Delete Selected')]"));
    }

    private WebElement getAddressesDeleteAllButton() {
        return getAddressesTableDropDown().findElement(By.xpath("//button[contains(text(), 'Delete Selected')]"));
    }

    private WebElement getAddressesPurgeAllButton() {
        return getAddressesTableDropDown().findElement(By.xpath("//button[contains(text(), 'Purge Selected')]"));
    }

    private WebElement getConnectionsCloseAllButton() {
        return getConnectionTableDropDown().findElement(By.xpath("//button[contains(text(), 'Close Selected')]"));
    }

    private WebElement getAddressesDeleteConfirmButton() {
        return selenium.getDriver().findElement(By.className("pf-c-backdrop"))
                .findElement(By.className("pf-c-modal-box"))
                .findElement(By.xpath("//button[contains(text(), 'Confirm')]"));
    }

    public WebElement getDangerAlertElement() {
        return selenium.getDriver().findElement(By.xpath("//div[@aria-label='Danger Alert']"));
    }

    private WebElement getHelpButton() {
        return selenium.getDriver().findElement(By.xpath("//a[contains(text(), 'Help')]"));
    }

    private WebElement getApplicationsButton() {
        return selenium.getDriver().findElement(By.xpath("//button[@aria-label='Applications']"));
    }

    private WebElement getApplicationsElem() {
        return selenium.getDriver().findElement(By.xpath("//ul[@role='menu']"));
    }

    public WebElement getEditAddrPlan() {
        return selenium.getDriver().findElement(By.id("edit-addr-plan"));
    }

    private WebElement getAddressPlanItem(String plan) {
        return selenium.getDriver().findElement(By.xpath("//option[@value='" + plan + "']"));
    }

    private WebElement getEditAuthService() {
        return selenium.getDriver().findElement(By.id("edit-addr-auth"));
    }
    //==============================================================

    //Items selectors
    public List<AddressSpaceWebItem> getAddressSpaceItems() {
        List<AddressSpaceWebItem> addressSpaceItems = new ArrayList<>();
        try {
            List<WebElement> elements = getAddressSpaceList().findElements(By.tagName("tr"));
            for (WebElement element : elements) {
                AddressSpaceWebItem addressSpace = new AddressSpaceWebItem(element);
                log.info(String.format("Got addressSpace: %s", addressSpace.toString()));
                addressSpaceItems.add(addressSpace);
            }
        } catch (Exception ex) {
            log.warn("No addressspace items found");
        }
        return addressSpaceItems;
    }

    public Boolean isClientListEmpty() {
        return (selenium.getDriver().findElements(By.xpath("//div[@class='pf-c-empty-state']")).size() == 1);
    }

    public AddressSpaceWebItem getAddressSpaceItem(AddressSpace as) {
        AddressSpaceWebItem returnedElement = null;
        List<AddressSpaceWebItem> addressWebItems = getAddressSpaceItems();
        for (AddressSpaceWebItem item : addressWebItems) {
            if (item.getName().equals(as.getMetadata().getName()) && item.getNamespace().equals(as.getMetadata().getNamespace()))
                returnedElement = item;
        }
        return returnedElement;
    }

    public List<AddressWebItem> getAddressItems() {
        List<AddressWebItem> addressSpaceItems = new ArrayList<>();
        try {
            List<WebElement> elements = getTableAddressList().findElements(By.tagName("tr"));
            for (WebElement element : elements) {
                AddressWebItem address = new AddressWebItem(element);
                log.info(String.format("Got address: %s", address.toString()));
                addressSpaceItems.add(address);
            }
        } catch (Exception ex) {
            log.warn("No address items found");
        }
        return addressSpaceItems;
    }

    public AddressWebItem getAddressItem(Address as) {
        AddressWebItem returnedElement = null;
        List<AddressWebItem> addressWebItems = getAddressItems();
        for (AddressWebItem item : addressWebItems) {
            if (item.getAddress().equals(as.getSpec().getAddress()))
                returnedElement = item;
        }
        return returnedElement;
    }

    public EndpointItem getEndpointItem(String name) {
        EndpointItem returnedElement = null;
        List<EndpointItem> addressWebItems = getEndpointItems();
        for (EndpointItem item : addressWebItems) {
            if (item.getName().equals(name))
                returnedElement = item;
        }
        return returnedElement;
    }

    public List<EndpointItem> getEndpointItems() {
        List<EndpointItem> endpoints = new ArrayList<>();
        try {
            List<WebElement> elements = getEndpointsList().findElements(By.tagName("tr"));
            for (WebElement element : elements) {
                EndpointItem endpoint = new EndpointItem(element);
                log.info(String.format("Got endpoint: %s", endpoint.toString()));
                endpoints.add(endpoint);
            }
        } catch (Exception ex) {
            log.warn("No endpoint items found");
        }
        return endpoints;
    }

    public List<ConnectionWebItem> getConnectionItems() {
        Supplier<List<ConnectionWebItem>> getter = () -> {
            List<WebElement> elements = getTableConnectionList().findElements(By.tagName("tr"));
            List<ConnectionWebItem> connections = new ArrayList<>();
            for (WebElement element : elements) {
                ConnectionWebItem connection = new ConnectionWebItem(element);
                log.info(String.format("Got connection: %s", connection.toString()));
                connections.add(connection);
            }
            return connections;
        };

        int attempts = 0;
        while (attempts < 5) {
            try {
                return getter.get();
            } catch (StaleElementReferenceException e) {
                log.info("StaleElementReferenceException during getConnectionItems() - retrying");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.info("", e);
            }
            attempts++;
        }
        throw new IllegalStateException("Unable to get connection items from connections table without getting StaleElementReferenceException");
    }

    public ConnectionWebItem getConnectionItem(String host) {
        ConnectionWebItem returnedElement = null;
        List<ConnectionWebItem> connections = getConnectionItems();
        for (ConnectionWebItem item : connections) {
            if (item.getHost().equals(host))
                returnedElement = item;
        }
        return returnedElement;
    }

    public List<ClientWebItem> getClientItems() {
        List<WebElement> elements = getTableClientsList().findElements(By.tagName("tr"));
        List<ClientWebItem> clients = new ArrayList<>();
        for (WebElement element : elements) {
            ClientWebItem client = new ClientWebItem(element);
            log.info(String.format("Got client: %s", client.toString()));
            clients.add(client);
        }
        return clients;
    }
    //==============================================================

    //Form selectors
    private WebElement getNamespaceDropDown() throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("cas-dropdown-namespace")));
    }

    private WebElement getAuthServiceDropDown() throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("cas-dropdown-auth-service")));
    }

    private WebElement getAddressSpaceNameInput() {
        return selenium.getDriver().findElement(By.id("address-space"));
    }

    private WebElement getBrokeredRadioButton() {
        return selenium.getDriver().findElement(By.id("cas-brokered-radio"));
    }

    private WebElement getStandardRadioButton() {
        return selenium.getDriver().findElement(By.id("cas-standard-radio"));
    }

    private WebElement getCustomizeEndpointSwitch() throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("asc-switch-customize-endpoint")));
    }

    private WebElement getProtocolCheckBox(Protocol protocol) throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("checkbox-key-" + protocol.toString().toLowerCase())));
    }

    private WebElement getTlsCertificatesCheckBox(String type) throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("radio-key-" + type)));
    }

    private WebElement getTslTerminations(Protocol protocol, TlsTerminationType type) throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("radio-key-" + type.toString().toLowerCase() + "-" + protocol.toString().toLowerCase())));
    }

    private WebElement getCertAreaInput() throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("text-file-upload-certificate")));
    }

    private WebElement getKeyAreaInput() throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("text-file-uplod-private-key")));
    }

    private WebElement getEnableRoutesSwitch() throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("switch-configure-route-btn")));
    }

    private WebElement getPlanDropDown() {
        return selenium.getDriver().findElement(By.id("cas-dropdown-plan"));
    }

    private WebElement getNextButton() {
        return selenium.getDriver().findElement(By.xpath("//button[contains(text(), 'Next')]"));
    }

    private WebElement getFinishButton() {
        return selenium.getDriver().findElement(By.xpath("//button[contains(text(), 'Finish')]"));
    }

    private WebElement getConfirmButton() {
        return selenium.getDriver().findElement(By.xpath("//button[contains(text(), 'Confirm')]"));
    }

    private WebElement getAddressNameInput() throws Exception {
        return selenium.getWebElement(() -> selenium.getDriver().findElement(By.id("address-name")));
    }

    private WebElement getAddressPlanDropDown() {
        return selenium.getDriver().findElement(By.id("address-definition-plan-dropdown"));
    }

    private WebElement getAddressTypeDropDown() {
        return selenium.getDriver().findElement(By.id("address-definition-type-dropdown"));
    }

    private WebElement getTopicSelectDropDown() {
        return selenium.getDriver().findElement(By.id("address-definition-topic-dropdown"));
    }

    //Filter selectors
    private WebElement getToolBarMenu() throws Exception {
        return selenium.getWebElement(() -> getContentElem().findElement(By.id("data-toolbar-with-filter")));
    }

    private WebElement getAddressFilterDropDown() throws Exception {
        return getToolBarMenu().findElement(By.id("al-filter-dropdown"));
    }

    private WebElement getConnectionFilterDropDown() throws Exception {
        return getToolBarMenu().findElement(By.id("cl-filter-dropdown"));
    }

    private WebElement getClientFilterDropDown() throws Exception {
        return getToolBarMenu().findElement(By.id("ad-links-filter-dropdown"));
    }

    private WebElement getSelectNameTextBox() throws Exception {
        return getToolBarMenu().findElement(By.tagName("input"));
    }

    private WebElement getSelectTypeDropDown() throws Exception {
        try {
            return getToolBarMenu().findElement(By.id("al-filter-select-type-dropdown"));
        } catch (Exception ex) {
            return getToolBarMenu().findElement(By.id("al-filter-dropdown-type"));
        }
    }

    private WebElement getSelectStatusDropDown() throws Exception {
        try {
            return getToolBarMenu().findElement(By.id("al-filter-dropdown-status"));
        } catch (Exception ex) {
            return getToolBarMenu().findElement(By.id("al-filter-select-status-dropdown"));
        }
    }

    private WebElement getTypeFilterDropDownItem() throws Exception {
        return getAddressFilterDropDown().findElement(By.id("al-filter-dropdowntype"));
    }

    private WebElement getStatusFilterDropDownItem() throws Exception {
        return getToolBarMenu().findElement(By.id("al-filter-dropdownstatus"));
    }

    private WebElement getAddressFilterDropDownItem() throws Exception {
        return getAddressFilterDropDown().findElement(By.id("al-filter-dropdownname"));
    }

    private WebElement getNamespaceFilterDropDownItem() throws Exception {
        return getAddressFilterDropDown().findElement(By.id("al-filter-dropdownnamespace"));
    }

    private WebElement getNameFilterDropDownItem() throws Exception {
        return getAddressFilterDropDown().findElement(By.id("al-filter-dropdownname"));
    }

    private WebElement getSearchButtonAddress() throws Exception {
        return getToolBarMenu().findElement(By.id("al-filter-select-name-search"));
    }

    private WebElement getSearchButtonNamespace() throws Exception {
        return getToolBarMenu().findElement(By.id("al-filter-search-namespace"));
    }

    private WebElement getSearchButtonName() throws Exception {
        return getToolBarMenu().findElement(By.id("al-filter-search-name"));
    }

    private WebElement getConnectionsHostnameFilterDropDownItem() throws Exception {
        return getConnectionFilterDropDown().findElement(By.id("cl-filter-dropdown-itemhostname"));
    }

    private WebElement getConnectionsContainerFilterDropDownItem() throws Exception {
        return getConnectionFilterDropDown().findElement(By.id("cl-filter-dropdown-itemcontainer"));
    }

    private WebElement getClientsContainerFilterDropDownItem() throws Exception {
        return getClientFilterDropDown().findElement(By.id("ad-links-filter-dropdown-itemcontainers"));
    }

    private WebElement getClientsNameFilterDropDownItem() throws Exception {
        return getClientFilterDropDown().findElement(By.id("ad-links-filter-dropdown-itemname"));
    }

    private WebElement getClientsRoleFilterDropDownItem() throws Exception {
        return getClientFilterDropDown().findElement(By.id("ad-links-filter-dropdown-itemrole"));
    }

    private WebElement getClientsContainerSearchButton() throws Exception {
        return getToolBarMenu().findElement(By.id("ad-links-filter-search-container"));
    }

    private WebElement getClientsNameSearchButton() throws Exception {
        return getToolBarMenu().findElement(By.id("ad-links-filter-search-name"));
    }

    private WebElement getConnectionsHostnameSearchButton() throws Exception {
        return getToolBarMenu().findElement(By.id("cl-filter-search-btn"));
    }

    private WebElement getConnectionsContainerSearchButton() throws Exception {
        return getToolBarMenu().findElement(By.id("cl-filter-search"));
    }

    private WebElement getAppliedFilterBar() throws Exception {
        return getToolBarMenu().findElements(By.className("pf-c-data-toolbar__content")).get(1); //TODO use id when will be implemented
    }

    private List<WebElement> getAppliedFilterItems() throws Exception {
        return getAppliedFilterBar().findElements(By.className("pf-m-toolbar")); //TODO use id when will be implemented
    }

    private WebElement getAppliedFilterItem(FilterType filterType, String filterValue) throws Exception {
        List<WebElement> filters = getAppliedFilterItems();
        for (WebElement filter : filters) {
            String typeOfFilter = filter.findElement(By.tagName("h4")).getText().toLowerCase();
            String itemFilterValue = filter.findElement(By.tagName("span")).getText().toLowerCase();
            if (typeOfFilter.equals(filterType.toString()) && itemFilterValue.equals(filterValue.toLowerCase())) {
                return filter;
            }
        }
        return null;
    }

    public WebElement getLinkContainerId() {
        return selenium.getDriver().findElement(By.id("cd-header-container-id"));
    }

    public WebElement getAddressSpaceTitle() {
        return selenium.getDriver().findElement(By.id("as-header-title"));
    }

    public WebElement getAddressTitle() {
        return selenium.getDriver().findElement(By.id("adheader-name"));
    }

    public void awaitGoneAwayPage() {
        selenium.getDriverWait().withTimeout(Duration.ofSeconds(120)).until(ExpectedConditions.visibilityOfElementLocated(ConsoleWebPage.NOT_FOUND_STATE_XPATH));
        selenium.takeScreenShot();
    }

    private WebElement getAuthServiceElement(String authService) {
        return selenium.getDriver()
                .findElement(By.xpath("//option[@value='" + authService + "']"));
    }

    private WebElement getEditConfirmButton() {
        return selenium.getDriver().findElement(By.id("as-list-edit-confirm"));
    }

    private WebElement getAddressSpacePlan(String addressSpacePlan) {
        return selenium.getDriver()
                .findElement(By.xpath("//option[@value='" + addressSpacePlan + "']"));
    }

    private List<WebElement> getDeploymentSnippetLines() {
        return selenium.getDriver().findElements(By.xpath("//div[@class='ace_line']"));
    }

    //==================================================================


    //================================================================================================
    // Operations
    //================================================================================================

    public void openConsolePage() throws Exception {
        log.info("Opening global console on route {}", ocRoute);
        selenium.getDriver().get(ocRoute);
        if (waitUntilLoginPage()) {
            selenium.takeScreenShot();
            try {
                logout();
            } catch (Exception ex) {
                log.info("User is not logged");
            }
            if (!login())
                throw new IllegalAccessException(loginPage.getAlertMessage());
        }
        if (!waitUntilConsolePage()) {
            throw new IllegalStateException("Openshift console not loaded");
        }
    }

    public WebElement getFirstLineOfDeploymentSnippet() {
        List<WebElement> snippetElements = getDeploymentSnippetLines();
        return snippetElements.get(0);
    }

    public String getDeploymentSnippet() throws InterruptedException {
        final int RETRY_COUNTER = 5;

        StringBuilder addressSpaceDeployment = new StringBuilder();
        for (int i = 0; i < RETRY_COUNTER && addressSpaceDeployment.toString().isEmpty(); i++) {
            List<WebElement> snippetElements = getDeploymentSnippetLines();

            for (WebElement currentElement : snippetElements) {
                if (currentElement.getText().contains(KubeCMDClient.getCMD())
                        || currentElement.getText().isEmpty() || currentElement.getText().equals("EOF")) {
                    continue;
                } else if (addressSpaceDeployment.length() == 0) {
                    addressSpaceDeployment = new StringBuilder(currentElement.getText());
                } else {
                    addressSpaceDeployment.append(System.lineSeparator()).append(currentElement.getText());
                }

            }
            Thread.sleep(2000);
        }
        return addressSpaceDeployment.toString();
    }

    public void openAddressList(AddressSpace addressSpace) throws Exception {
        AddressSpaceWebItem item = selenium.waitUntilItemPresent(30, () -> getAddressSpaceItem(addressSpace));
        selenium.clickOnItem(item.getConsoleRoute());
        selenium.getWebElement(this::getAddressTable);
    }

    public void openConnectionList(AddressSpace addressSpace) throws Exception {
        AddressSpaceWebItem item = selenium.waitUntilItemPresent(30, () -> getAddressSpaceItem(addressSpace));
        selenium.clickOnItem(item.getConsoleRoute());
        switchToConnectionTab();
        selenium.getWebElement(this::getConnectionTable);
    }

    public void openEndpointList(AddressSpace addressSpace) throws Exception {
        AddressSpaceWebItem item = selenium.waitUntilItemPresent(30, () -> getAddressSpaceItem(addressSpace));
        selenium.clickOnItem(item.getConsoleRoute());
        switchToEndpointTab();
        selenium.getWebElement(this::getEndpointsTable);
    }

    public void openConnection(String hostname) throws Exception {
        ConnectionWebItem item = selenium.waitUntilItemPresent(30, () -> getConnectionItem(hostname));
        selenium.clickOnItem(item.getHostRoute());

    }

    public void openClientsList(Address address) throws Exception {
        AddressWebItem item = selenium.waitUntilItemPresent(30, () -> getAddressItem(address));
        selenium.clickOnItem(item.getClientsRoute(), "Clients route");
        selenium.getWebElement(this::getClientsTable);
    }

    private void selectNamespace(String namespace) throws Exception {
        selenium.clickOnItem(getNamespaceDropDown(), "namespace dropdown");
        selenium.clickOnItem(selenium.getDriver().findElement(By.xpath("//button[@value='" + namespace + "']")), namespace);
    }

    private void selectPlan(String plan) throws Exception {
        selenium.clickOnItem(getPlanDropDown(), "address space plan dropdown");
        selenium.clickOnItem(selenium.getDriver().findElement(By.xpath("//button[@value='" + plan + "']")), plan);
    }

    private void selectAuthService(String authService) throws Exception {
        selenium.clickOnItem(getAuthServiceDropDown(), "address space plan dropdown");
        selenium.clickOnItem(selenium.getDriver().findElement(By.xpath("//button[@value='" + authService + "']")), authService);
    }

    public void prepareAddressSpaceInstall(AddressSpace addressSpace) throws Exception {
        selenium.clickOnItem(getCreateButtonTop());
        selectNamespace(addressSpace.getMetadata().getNamespace());
        selenium.fillInputItem(getAddressSpaceNameInput(), addressSpace.getMetadata().getName());
        selenium.clickOnItem(addressSpace.getSpec().getType().equals(AddressSpaceType.BROKERED.toString().toLowerCase()) ? getBrokeredRadioButton() : getStandardRadioButton(),
                addressSpace.getSpec().getType());
        selectPlan(addressSpace.getSpec().getPlan());
        selectAuthService(addressSpace.getSpec().getAuthenticationService().getName());
        if (addressSpace.getSpec().getEndpoints().size() > 0) {
            enableEndpointCustomization();
        }
        selenium.clickOnItem(getNextButton());
        if (addressSpace.getSpec().getEndpoints().size() > 0) {
            boolean expose = true;
            boolean customCert = false;
            for (EndpointSpec endpoint : addressSpace.getSpec().getEndpoints()) {
                selectProtocols(Protocol.valueOf(serviceName2protocol(endpoint.getService()).toUpperCase()));
                selectTls(endpoint.getCert().getProvider());
                if (!endpoint.getExpose().getType().equals(ExposeType.route)) {
                    disableDefaultRoutes();
                    expose = false;
                }
                customCert = endpoint.getCert().getProvider().equals(CertProvider.certBundle.name());
            }
            selenium.clickOnItem(getNextButton());
            if (customCert) {
                for (EndpointSpec endpoint : addressSpace.getSpec().getEndpoints()) {
                    insertCertificate(new String(Base64.decodeBase64(endpoint.getCert().getTlsCert())));
                    insertPrivateKey(new String(Base64.decodeBase64(endpoint.getCert().getTlsKey())));
                }
                selenium.clickOnItem(getNextButton());
            }
            if (expose) {
                for (EndpointSpec endpoint : addressSpace.getSpec().getEndpoints()) {
                    selectTlsTermination(Protocol.valueOf(serviceName2protocol(endpoint.getService()).toUpperCase()),
                            TlsTerminationType.valueOf(endpoint.getExpose().getRouteTlsTermination().toString().toUpperCase()));
                }
                selenium.clickOnItem(getNextButton());
            }
        }
    }

    public void createAddressSpace(AddressSpace addressSpace) throws Exception {
        prepareAddressSpaceInstall(addressSpace);
        selenium.clickOnItem(getFinishButton());
        selenium.waitUntilItemPresent(30, () -> getAddressSpaceItem(addressSpace));
        selenium.takeScreenShot();
        AddressSpaceUtils.waitForAddressSpaceReady(addressSpace);
        AddressSpaceUtils.syncAddressSpaceObject(addressSpace);
        selenium.refreshPage();
    }

    public void deleteAddressSpace(AddressSpace addressSpace) throws Exception {
        AddressSpaceWebItem item = selenium.waitUntilItemPresent(30, () -> getAddressSpaceItem(addressSpace));
        selenium.clickOnItem(item.getActionDropDown(), "Address space dropdown");
        selenium.clickOnItem(item.getDeleteMenuItem());
        selenium.clickOnItem(getConfirmButton());
        selenium.waitUntilItemNotPresent(30, () -> getAddressSpaceItem(addressSpace));
    }

    public void changeAddressSpacePlan(AddressSpace addressSpace, String addressSpacePlan) throws Exception {
        AddressSpaceWebItem item = selenium.waitUntilItemPresent(30, () -> getAddressSpaceItem(addressSpace));
        selenium.clickOnItem(item.getActionDropDown(), "Address space dropdown");
        selenium.clickOnItem(item.getEditMenuItem());
        selenium.clickOnItem(getEditAddrPlan());
        selenium.clickOnItem(getAddressSpacePlan(addressSpacePlan));
        selenium.clickOnItem(getEditConfirmButton());
        selenium.refreshPage();
        addressSpace.getSpec().setPlan(addressSpacePlan);
    }

    public void changeAuthService(AddressSpace addressSpace, String authServiceName, AuthenticationServiceType type) throws Exception {
        AddressSpaceWebItem item = selenium.waitUntilItemPresent(30, () -> getAddressSpaceItem(addressSpace));
        selenium.clickOnItem(item.getActionDropDown(), "AddressSpaceDropdown");
        selenium.clickOnItem(item.getEditMenuItem());
        selenium.clickOnItem(getEditAuthService());
        selenium.clickOnItem(getAuthServiceElement(authServiceName));
        selenium.clickOnItem(getEditConfirmButton());
        selenium.refreshPage();
        addressSpace.getSpec().getAuthenticationService().setName(authServiceName);
        addressSpace.getSpec().getAuthenticationService().setType(type);
    }

    public void createAddressesAndWait(Address... addresses) throws Exception {
        for (Address address : addresses) {
            createAddress(address, false);
        }
        AddressUtils.waitForDestinationsReady(addresses);
    }

    public void createAddresses(Address... addresses) throws Exception {
        for (Address address : addresses) {
            createAddress(address, false);
        }
    }

    public void createAddress(Address address) throws Exception {
        createAddress(address, true);
    }

    public void enableEndpointCustomization() throws Exception {
        selenium.switchCheckBox(getCustomizeEndpointSwitch(), "Customize endpoints");
    }

    public void disableDefaultRoutes() throws Exception {
        selenium.switchCheckBox(getEnableRoutesSwitch(), "Disable default routes");
    }

    public void selectProtocols(Protocol... protocols) throws Exception {
        for (Protocol protocol : protocols) {
            selenium.clickOnItem(getProtocolCheckBox(protocol), protocol.toString());
        }
    }

    public void selectTls(String tls) throws Exception {
        selenium.clickOnItem(getTlsCertificatesCheckBox(tls), tls);
    }

    public void selectTlsTermination(Protocol protocol, TlsTerminationType type) throws Exception {
        selenium.clickOnItem(getTslTerminations(protocol, type), String.format("%s:%s", protocol.toString().toLowerCase(), type.toString().toLowerCase()));
    }

    public void insertCertificate(String certificate) throws Exception {
        selenium.fillInputItem(getCertAreaInput(), certificate);
    }

    public void insertPrivateKey(String privateKey) throws Exception {
        selenium.fillInputItem(getKeyAreaInput(), privateKey);
    }

    public void prepareAddressCreation(Address address) throws Exception {
        selenium.clickOnItem(getCreateButtonTop());
        selenium.fillInputItem(getAddressNameInput(), address.getSpec().getAddress());
        selenium.clickOnItem(getAddressTypeDropDown(), "Address Type dropdown");
        selenium.clickOnItem(getAddressTypeDropDown().findElement(By.id("address-definition-type-dropdown-item" + address.getSpec().getType())));
        selenium.clickOnItem(getAddressPlanDropDown(), "address plan dropdown");
        selenium.clickOnItem(getAddressPlanDropDown().findElement(By.id("address-definition-plan-dropdown-item" + address.getSpec().getPlan())));
        if (address.getSpec().getType().equals(AddressType.SUBSCRIPTION.toString())) {
            selenium.clickOnItem(getTopicSelectDropDown(), "topic dropdown");
            selenium.clickOnItem(getTopicSelectDropDown().findElement(By.id("address-definition-topic-dropdown-item" + address.getSpec().getTopic())));
        }
        selenium.clickOnItem(getNextButton());
    }

    public void createAddress(Address address, boolean waitForReady) throws Exception {
        log.info("Address {} will be created using web console", address);
        prepareAddressCreation(address);
        selenium.clickOnItem(getFinishButton());
        selenium.waitUntilItemPresent(30, () -> getAddressItem(address));
        if (waitForReady) {
            AddressUtils.waitForDestinationsReady(address);
        }
    }

    public void openAddressCreationDialog() throws Exception {
        selenium.clickOnItem(getCreateButtonTop());
    }

    public void fillAddressName(String name) throws Exception {
        selenium.fillInputItem(getAddressNameInput(), name);
    }

    public boolean isAddressNameInvalid() throws Exception {
        String value = getAddressNameInput().getAttribute("aria-invalid");
        return value.equals("true");
    }

    public void deleteAddress(Address dest) throws Exception {
        log.info("Address {} will be deleted using web console", dest);
        AddressWebItem item = getAddressItem(dest);
        selenium.clickOnItem(item.getActionDropDown(), "Address item menu");
        selenium.clickOnItem(item.getDeleteMenuItem());
        selenium.clickOnItem(getConfirmButton());
        selenium.waitUntilItemNotPresent(30, () -> getAddressItem(dest));
        AddressUtils.waitForAddressDeleted(dest, new TimeoutBudget(5, TimeUnit.MINUTES));
    }

    public void switchToAddressTab() {
        selenium.clickOnItem(getAddressTab(), "Addresses");
        selenium.getDriverWait().withTimeout(Duration.ofSeconds(60)).until(ExpectedConditions.visibilityOfElementLocated(ADDRESS_LIST_XPATH));
        selenium.takeScreenShot();
    }

    public void switchToConnectionTab() {
        selenium.clickOnItem(getConnectionTab(), "Connections");
        selenium.getDriverWait().withTimeout(Duration.ofSeconds(60)).until(ExpectedConditions.visibilityOfElementLocated(CONNECTION_LIST_XPATH));
        selenium.takeScreenShot();
    }

    public void switchToEndpointTab() {
        selenium.clickOnItem(getEndpointTab(), "Endpoints");
        selenium.getDriverWait().withTimeout(Duration.ofSeconds(60)).until(ExpectedConditions.visibilityOfElementLocated(ENDPOINT_LIST_XPATH));
        selenium.takeScreenShot();
    }

    public void addFilter(FilterType filterType, String filterValue) throws Exception {
        log.info("Apply filter {} type {}", filterValue, filterType);
        selenium.clickOnItem(getAddressFilterDropDown(), "Address filter dropdown");
        switch (filterType) {
            case ADDRESS:
                selenium.clickOnItem(getAddressFilterDropDownItem());
                selenium.fillInputItem(getSelectNameTextBox(), filterValue);
                selenium.clickOnItem(getSearchButtonAddress(), "Search");
                break;
            case STATUS:
                selenium.clickOnItem(getStatusFilterDropDownItem());
                selenium.clickOnItem(getSelectStatusDropDown(), "Status phase dropdown");
                try {
                    selenium.clickOnItem(getSelectStatusDropDown()
                            .findElement(By.id("al-filter-select-status-dropdown-item" + filterValue.toLowerCase())));
                } catch (Exception ex) {
                    selenium.clickOnItem(getSelectStatusDropDown()
                            .findElement(By.id("al-filter-dropdown-item-status" + filterValue.toLowerCase())));
                }
                break;
            case NAME:
                selenium.clickOnItem(getNameFilterDropDownItem());
                selenium.fillInputItem(getSelectNameTextBox(), filterValue);
                selenium.clickOnItem(getSearchButtonName(), "Search");
                break;
            case NAMESPACE:
                selenium.clickOnItem(getNamespaceFilterDropDownItem());
                selenium.fillInputItem(getSelectNameTextBox(), filterValue);
                selenium.clickOnItem(getSearchButtonNamespace(), "Search");
                break;
            case TYPE:
                selenium.clickOnItem(getTypeFilterDropDownItem());
                selenium.clickOnItem(getSelectTypeDropDown(), "Type filter dropdown");
                WebElement selectedType;
                try {
                    selectedType = getSelectTypeDropDown()
                            .findElement(By.id("al-filter-select-type-dropdown-item" + filterValue.toLowerCase()));
                } catch (Exception ex) {
                    selectedType = getSelectTypeDropDown()
                            .findElement(By.id("al-filter-dropdown-item-type" + filterValue.toLowerCase()));
                }
                selenium.clickOnItem(selectedType);
                break;
        }
    }

    public void addConnectionsFilter(FilterType filterType, String filterValue) throws Exception {
        selenium.clickOnItem(getConnectionFilterDropDown(), "Connections filter dropdown");
        switch (filterType) {
            case HOSTNAME:
                selenium.clickOnItem(getConnectionsHostnameFilterDropDownItem());
                selenium.fillInputItem(getSelectNameTextBox(), filterValue);
                selenium.clickOnItem(getConnectionsHostnameSearchButton(), "Search");
                break;
            case CONTAINER:
                selenium.clickOnItem(getConnectionsContainerFilterDropDownItem());
                selenium.fillInputItem(getSelectNameTextBox(), filterValue);
                selenium.clickOnItem(getConnectionsContainerSearchButton(), "Search");
                break;
        }
    }


    public void addClientsFilter(FilterType filterType, String filterValue) throws Exception {
        selenium.clickOnItem(getClientFilterDropDown(), "Connections filter dropdown");
        switch (filterType) {
            case NAME:
                selenium.clickOnItem(getClientsNameFilterDropDownItem());
                selenium.fillInputItem(getSelectNameTextBox(), filterValue);
                selenium.clickOnItem(getClientsNameSearchButton(), "Search");
                break;
            case CONTAINER:
                selenium.clickOnItem(getClientsContainerFilterDropDownItem());
                selenium.fillInputItem(getSelectNameTextBox(), filterValue);
                selenium.clickOnItem(getClientsContainerSearchButton(), "Search");
                break;
        }
    }

    public void removeAllFilters() throws Exception {
        log.info("Clear all filters");
        selenium.clickOnItem(getToolBarMenu().findElements(By.tagName("button")).stream().filter(webElement -> webElement.getText().contains("Clear all filters")).findAny().get());
    }

    public void removeAddressFilter(FilterType filterType, String filterValue) throws Exception {
        log.info("Removing filter {} type {}", filterValue, filterType);
        selenium.clickOnItem(Objects.requireNonNull(getAppliedFilterItem(filterType, filterValue)).findElement(By.tagName("button")), "delete filter");
    }

    public void selectAddress(Address address) {
        selenium.clickOnItem(getAddressItem(address).getCheckBox(), "Select address");
    }

    public void selectAddressSpace(AddressSpace addressSpace) {
        selenium.clickOnItem(getAddressSpaceItem(addressSpace).getCheckBox(), "Select address space");
    }

    public void selectAddresses(Address... addresses) {
        for (Address address : addresses) {
            selectAddress(address);
        }
    }

    public void selectAddressSpaces(AddressSpace... addressSpacess) {
        for (AddressSpace addressSpace : addressSpacess) {
            selectAddressSpace(addressSpace);
        }
    }

    public void deleteSelectedAddressSpaces(AddressSpace... addressSpaces) throws Exception {
        selectAddressSpaces(addressSpaces);
        selenium.clickOnItem(getAddressSpacesTableDropDown(), "Main dropdown");
        selenium.clickOnItem(getAddressSpacesDeleteAllButton());
        selenium.clickOnItem(getConfirmButton());

        int timeAllowed = 30 * addressSpaces.length;
        for (AddressSpace space : addressSpaces) {
            selenium.waitUntilItemNotPresent(timeAllowed, () -> getAddressSpaceItem(space));
        }
    }

    public void deleteSelectedAddresses(Address... addresses) throws Exception {
        selectAddresses(addresses);
        selenium.clickOnItem(getAddressesTableDropDown(), "Main dropdown");
        selenium.clickOnItem(getAddressesDeleteAllButton(), "Deleted selected");
        selenium.clickOnItem(getAddressesDeleteConfirmButton());
        for (Address address : addresses) {
            selenium.waitUntilItemNotPresent(30, () -> getAddressItem(address));
        }
    }

    public void purgeSelectedAddresses(Address... addresses) {
        selectAddresses(addresses);
        selenium.clickOnItem(getAddressesTableDropDown(), "Main dropdown");
        selenium.clickOnItem(getAddressesPurgeAllButton());
        selenium.clickOnItem(getConfirmButton());
    }

    public void closeSelectedConnection(ConnectionWebItem... connectionWebItems) {
        Arrays.stream(connectionWebItems).forEach(c -> {
            selenium.clickOnItem(c.getCheckBox(), "Select connection");

        });
        selenium.clickOnItem(getConnectionTableDropDown(), "Main dropdown");
        selenium.clickOnItem(getConnectionsCloseAllButton());
        selenium.clickOnItem(getConfirmButton());
    }

    public void changeAddressPlan(Address address, String plan) throws Exception {
        AddressWebItem item = selenium.waitUntilItemPresent(30, () -> getAddressItem(address));
        selenium.clickOnItem(item.getActionDropDown(), "Action drop down");
        selenium.clickOnItem(item.getEditMenuItem(), "Edit");
        selenium.clickOnItem(selenium.getWebElement(this::getEditAddrPlan), "Editing address plan");
        selenium.clickOnItem(selenium.getWebElement(() -> getAddressPlanItem(plan)));
        selenium.clickOnItem(getConfirmButton());
    }

    public String getHelpLink() {
        selenium.clickOnItem(getApplicationsButton());

        return getApplicationsElem()
                .findElement(By.xpath("//a[@index='0']"))
                .getAttribute("href");
    }

    public void openHelpLink(String expectedUrl) {
        selenium.takeScreenShot();
        try {
            selenium.clickOnItem(selenium.getWebElement(this::getHelpButton));
            selenium.getDriverWait().withTimeout(Duration.ofSeconds(30)).until(ExpectedConditions.urlContains(expectedUrl));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            selenium.takeScreenShot();
        }
    }

    public void waitForErrorDialogToBePresent() throws Exception {
        selenium.getWebElement(this::getDangerAlertElement);
    }

    public void sortAddresses(SortType sortType, boolean asc) throws Exception {
        sortItems(sortType, asc, this::getTableAddressHeader, this::isAddressSortType);
    }

    public void sortConnections(SortType sortType, boolean asc) throws Exception {
        sortItems(sortType, asc, this::getTableConnectionHeader, this::isConnectionsSortType);
    }

    private void sortItems(SortType sortType, boolean asc, Supplier<WebElement> tableHeaderSupplier, BiPredicate<String, SortType> columnFilter) throws Exception {
        log.info("Sorting");

        String sortingDirection = asc ? "ascending" : "descending";

        Supplier<WebElement> columnHeaderSupplier = () -> tableHeaderSupplier.get().findElements(By.tagName("th")).stream()
                .filter(we -> columnFilter.test(we.getAttribute("data-label"), sortType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Column to sort not found"));

        var columnHeader = columnHeaderSupplier.get();
        int safeguard = 3;
        do {
            if (safeguard < 0) {
                selenium.takeScreenShot();
                throw new IllegalStateException("Sorting is not working");
            }
            selenium.clickOnItem(columnHeader.findElement(By.tagName("button")));
            Thread.sleep(500);
            columnHeader = columnHeaderSupplier.get();
            safeguard--;
        } while (!columnHeader.getAttribute("aria-sort").equals(sortingDirection));
        selenium.takeScreenShot();
    }

    private boolean isAddressSortType(String dataLabel, SortType sortType) {
        switch (sortType) {
            case MESSAGE_IN:
            case MESSAGE_OUT:
            case STORED_MESSAGES:
            case ADDRESS:
            case SENDERS:
            case RECEIVERS:
            case NAME:
                return dataLabel.toUpperCase().equals(sortType.toString());
            default:
                return false;
        }
    }

    private boolean isConnectionsSortType(String dataLabel, SortType sortType) {
        switch (sortType) {
            case MESSAGE_IN:
            case MESSAGE_OUT:
            case HOSTNAME:
            case CONTAINER_ID:
            case PROTOCOL:
            case TIME_CREATED:
            case SENDERS:
            case RECEIVERS:
                return dataLabel.toUpperCase().equals(sortType.toString());
            default:
                return false;
        }
    }

    //================================================================================================
    // Login
    //================================================================================================

    private boolean login() throws Exception {
        return loginPage.login(credentials.getUsername(), credentials.getPassword());
    }

    public void logout() {
        try {
            WebElement userDropdown = selenium.getDriver().findElement(By.id("dd-user"));
            selenium.clickOnItem(userDropdown, "User dropdown navigation");
            WebElement logout = selenium.getDriver().findElement(By.id("dd-menuitem-logout"));
            selenium.clickOnItem(logout, "Log out");
        } catch (Exception ex) {
            log.info("Unable to logout, user is not logged in");
        }
    }

    private boolean waitUntilLoginPage() {
        try {
            selenium.getDriverWait().withTimeout(Duration.ofSeconds(3)).until(ExpectedConditions.titleContains("Log"));
            try {
                selenium.clickOnItem(selenium.getWebElement(this::getLoginButton));
            } catch (Exception ex) {
                log.info("Only openshift auth provider is enabled");
            }
            return true;
        } catch (Exception ex) {
            selenium.takeScreenShot();
            return false;
        }
    }

    private boolean waitUntilConsolePage() {
        try {
            selenium.getDriverWait().until(ExpectedConditions.visibilityOfElementLocated(By.id("root")));
            return true;
        } catch (Exception ex) {
            selenium.takeScreenShot();
            log.info("Error waitUntilConsolePage", ex);
            return false;
        }
    }

    @Override
    public void checkReachableWebPage() {
        selenium.getDriverWait().withTimeout(Duration.ofSeconds(60)).until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.id("root")),
                ExpectedConditions.titleContains("Address Space List"),
                ExpectedConditions.titleContains("Address List")
        ));
    }

    public enum Protocol {
        AMQPS,
        HTTPS
    }

    public enum TlsTerminationType {
        PASSTHROUGH,
        REENCRYPT
    }

    private String serviceName2protocol(String serviceName) {
        if (serviceName.equals("messaging")) {
            return "amqps";
        } else if (serviceName.equals("messaging-wss")) {
            return "https";
        } else return "amqps";
    }
}
