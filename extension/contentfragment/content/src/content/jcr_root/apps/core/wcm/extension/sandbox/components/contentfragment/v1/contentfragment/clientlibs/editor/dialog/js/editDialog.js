/*******************************************************************************
 * Copyright 2017 Adobe Systems Incorporated
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
(function (window, $, channel, Granite, Coral) {
    "use strict";

    // class of the edit dialog content
    var CLASS_EDIT_DIALOG = "cmp-contentfragment__edit-dialog";

    // field selectors
    var SELECTOR_FRAGMENT_PATH = "[name='./fragmentPath']";
    var SELECTOR_ELEMENT_NAMES = "[data-granite-coral-multifield-name='./elementNames']";
    var SELECTOR_ELEMENT_NAMES_ADD = SELECTOR_ELEMENT_NAMES + " > [is=coral-button]";
    var SELECTOR_VARIATION_NAME = "[name='./variationName']";
    var SELECTOR_PARAGRAPH_CONTROLS = ".cmp-contentfragment__edit-dialog-paragraph-controls";
    var SELECTOR_PARAGRAPH_SCOPE = "[name='./paragraphScope']";
    var SELECTOR_PARAGRAPH_RANGE = "[name='./paragraphRange']";
    var SELECTOR_PARAGRAPH_HEADINGS = "[name='./paragraphHeadings']";

    // ui helper
    var ui = $(window).adaptTo("foundation-ui");

    // dialog texts
    var confirmationDialogTitle = Granite.I18n.get("Warning");
    var confirmationDialogMessage = Granite.I18n.get("Please confirm replacing the current content fragment and its configuration");
    var confirmationDialogCancel = Granite.I18n.get("Cancel");
    var confirmationDialogConfirm = Granite.I18n.get("Confirm");
    var errorDialogTitle = Granite.I18n.get("Error");
    var errorDialogMessage = Granite.I18n.get("Failed to load the elements of the selected content fragment");

    // the fragment path field (foundation autocomplete)
    var fragmentPath;
    // the element names field (multifield)
    var elementNames;
    // the add button of the element names field
    var addElement;
    // the variation name field (select)
    var variationName;
    // the paragraph controls (field set)
    var paragraphControls;

    // the path of the component being edited
    var componentPath;
    // the resource path of the element names field
    var elementNamesPath;
    // the resource path of the variation name field
    var variationNamePath;
    // the resource path of the paragraph controls field set
    var paragraphControlsPath;

    // keeps track of the current fragment path
    var currentFragmentPath;

    // initial state of the paragraph controls (if set)
    var initialParagraphScope;
    var initialParagraphRange;
    var initialParagraphHeadings;

    function initialize(dialog) {
        // get path of component being edited
        var content = dialog.querySelector("." + CLASS_EDIT_DIALOG);
        componentPath = content.dataset.componentPath;

        // get the fields
        fragmentPath = dialog.querySelector(SELECTOR_FRAGMENT_PATH);
        elementNames = dialog.querySelector(SELECTOR_ELEMENT_NAMES);
        addElement = elementNames.querySelector(SELECTOR_ELEMENT_NAMES_ADD);
        variationName = dialog.querySelector(SELECTOR_VARIATION_NAME);
        paragraphControls = dialog.querySelector(SELECTOR_PARAGRAPH_CONTROLS);

        // get the field resource paths from their data attribute
        elementNamesPath = elementNames.dataset.fieldPath;
        variationNamePath = variationName.dataset.fieldPath;
        paragraphControlsPath = paragraphControls.dataset.fieldPath;

        // initialize state variables
        currentFragmentPath = fragmentPath.value;
        var scope = paragraphControls.querySelector(SELECTOR_PARAGRAPH_SCOPE + "[checked]");
        if (scope) {
            initialParagraphScope = scope.value;
            initialParagraphRange = paragraphControls.querySelector(SELECTOR_PARAGRAPH_RANGE).value;
            initialParagraphHeadings = paragraphControls.querySelector(SELECTOR_PARAGRAPH_HEADINGS).value;
        }

        // disable add button and variation name if no content fragment is currently set
        if (!currentFragmentPath) {
            addElement.setAttribute("disabled", "");
            variationName.setAttribute("disabled", "");
        }
        // enable / disable the paragraph controls
        setParagraphControlsState();

        // register change listener
        $(fragmentPath).on("foundation-field-change", onFragmentPathChange);
        $(elementNames).on("change", updateParagraphControls);
        $(document).on("change", SELECTOR_PARAGRAPH_SCOPE, setParagraphControlsState);
    }

    /**
     * Executes after the fragment path has changed. Shows a confirmation dialog to the user if the current
     * configuration is to be reset and updates the fields to reflect the newly selected content fragment.
     */
    function onFragmentPathChange() {
        // if the fragment was reset (i.e. the fragment path was deleted)
        if (!fragmentPath.value) {
            // confirm change (if necessary)
            confirmFragmentChange(null, null, function () {
                // disable add button and variation name
                addElement.setAttribute("disabled", "");
                variationName.setAttribute("disabled", "");

                // update (hide) paragraph controls
                updateParagraphControls();
            });
            // don't do anything else
            return;
        }

        // get markup of element names and variation name fields, parameterizing their datasources with new fragment
        var data = { fragmentPath: fragmentPath.value };
        var elementNamesRequest = $.get({url: Granite.HTTP.externalize(elementNamesPath) + ".html", data: data});
        var variationNameRequest = $.get({url: Granite.HTTP.externalize(variationNamePath) + ".html", data: data});

        // wait for requests to load
        $.when(elementNamesRequest, variationNameRequest).then(function (result1, result2) {
            // get the fields from the resulting markup
            var newElementNames = $(result1[0]).find(SELECTOR_ELEMENT_NAMES)[0];
            var newVariationName = $(result2[0]).find(SELECTOR_VARIATION_NAME)[0];
            // wait for them to be ready
            Coral.commons.ready(newElementNames, function() {
                Coral.commons.ready(newVariationName, function() {
                    // confirm change (if necessary)
                    confirmFragmentChange(newElementNames, newVariationName, function () {
                        // replace the element names multifield's template
                        elementNames.template = newElementNames.template;

                        // replace the variation name select, keeping its value
                        newVariationName.value = variationName.value;
                        variationName.parentNode.replaceChild(newVariationName, variationName);
                        variationName = newVariationName;

                        // enable add button and variation name
                        addElement.removeAttribute("disabled");
                        variationName.removeAttribute("disabled");

                        // update paragraph controls
                        updateParagraphControls();
                    });
                });
            });
        }, function () {
            // display error dialog if one of the requests failed
            ui.prompt(errorDialogTitle, errorDialogMessage, "error");
        });
    }

    /**
     * Presents the user with a confirmation dialog if the current configuration needs to be reset as a result
     * of the content fragment change. Executes the specified callback after the user confirms, or if the current
     * configuration can be kept (in which case no dialog is shown).
     *
     * @param newElementNames the element names field reflecting the newly selected fragment (null if fragment was unset)
     * @param newVariationName the variation name field reflecting the newly selected fragment (null if fragment was unset)
     * @param callback a callback to execute after the change is confirmed
     */
    function confirmFragmentChange(newElementNames, newVariationName, callback) {
        // check if we can keep the current configuration, in which case no confirmation dialog is necessary
        if (canKeepConfig(newElementNames, newVariationName)) {
            // update the current fragment path
            currentFragmentPath = fragmentPath.value;
            // execute callback
            callback();
            return;
        }

        // else show a confirmation dialog
        ui.prompt(confirmationDialogTitle, confirmationDialogMessage, "warning", [{
            text: confirmationDialogCancel,
            handler: function () {
                // reset the fragment path to its previous value
                requestAnimationFrame(function() {
                    fragmentPath.value = currentFragmentPath;
                });
            }
        }, {
            text: confirmationDialogConfirm,
            primary: true,
            handler: function () {
                // reset the current configuration
                elementNames.items.clear();
                variationName.value = "";

                // update the current fragment path
                currentFragmentPath = fragmentPath.value;
                // execute callback
                callback();
            }
        }]);
    }


    /**
     * Checks if the current configuration of element names and variation name can be kept, or if it needs to be reset
     * as a result of the content fragment change. It compares the two fields to the new ones that reflect the newly
     * selected content fragment.
     *
     * @param newElementNames the element names field reflecting the newly selected fragment (null if fragment was unset)
     * @param newVariationName the variation name field reflecting the newly selected fragment (null if fragment was unset)
     * @returns {boolean} true if the configuration can be kept or if there was none, false otherwise
     */
    function canKeepConfig(newElementNames, newVariationName) {
        // check if some element names are currently configured
        if (elementNames.items.length > 0) {
            // if we're unsetting the current fragment we need to reset the config
            if (!newElementNames) {
                return false;
            }
            // compare the items of the current and new element names fields
            var currentItems = elementNames.template.content.querySelectorAll("coral-select-item");
            var newItems = newElementNames.template.content.querySelectorAll("coral-select-item");
            if (!itemsAreEqual(currentItems, newItems)) {
                return false;
            }
        }

        // check if a varation is currently configured
        if (variationName.value && variationName.value !== "master") {
            // if we're unsetting the current fragment we need to reset the config
            if (!newVariationName) {
                return false;
            }
            // compare the items of the current and new variation name fields
            if (!itemsAreEqual(variationName.items.getAll(), newVariationName.items.getAll())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compares two arrays containing select items, returning true if the arrays have the same size and all contained
     * items have the same value and label.
     */
    function itemsAreEqual(a1, a2) {
        // verify that the arrays have the same length
        if (a1.length !== a2.length) {
            return false;
        }
        for (var i = 0; i < a1.length; i++) {
            var item1 = a1[i];
            var item2 = a2[i];
            if (item1.attributes.value.value !== item2.attributes.value.value
                || item1.textContent !== item2.textContent) {
                // the values or labels of the current items didn't match
                return false;
            }
        }
        return true;
    }

    /**
     * Is called when the fragment or element names configuration changes. Retrieves the paragraph controls from the
     * server, which might now be hidden or displayed depending on the new configuration.
     */
    function updateParagraphControls () {
        // get array of configured element names
        var names = $(elementNames.items.getAll()).filter(function () {
            // filter empty entries of the element names multifield
            return this.content && this.content.querySelector("coral-select");
        }).map(function () {
            // map the valid entries to the selected element name
            return this.content.querySelector("coral-select").value;
        });

        if (names.length === 0 || names.length === 1) {
            // if zero or one element names are configured, we request the markup of paragraph controls,
            // parameterizing its rendercondition with the current fragment and element names
            var data = {
                componentPath: componentPath,
                fragmentPath: currentFragmentPath,
                elementName: names.length ? names[0] : ""
            };
            $.get({
                url: Granite.HTTP.externalize(paragraphControlsPath) + ".html",
                data: data
            }).done(function (html) {
                // add resulting (potentially empty) markup to the dialog
                paragraphControls.innerHTML = $(html).get(0).innerHTML;
                // set initial (persisted) state, if available
                if (paragraphControls.children.length && initialParagraphScope) {
                    paragraphControls.querySelector(SELECTOR_PARAGRAPH_SCOPE + "[value="+ initialParagraphScope +"]").click();
                    paragraphControls.querySelector(SELECTOR_PARAGRAPH_RANGE).value = initialParagraphRange;
                    paragraphControls.querySelector(SELECTOR_PARAGRAPH_HEADINGS).checked = !!initialParagraphHeadings;
                }
                // enable / disable the paragraph controls
                setParagraphControlsState();
            });
        } else {
            // if more than one element is configured, we remove the paragraph controls
            paragraphControls.innerHTML = "";
        }
    }

    /**
     * Enables or disables the paragraph range and headings field depending on the state of the paragraph scope field.
     */
    function setParagraphControlsState () {
        // get the selected scope radio button (might not be present at all)
        var scope = paragraphControls.querySelector(SELECTOR_PARAGRAPH_SCOPE + "[checked]");
        if (scope) {
            // enable or disable range and headings fields according to the scope value
            var range = paragraphControls.querySelector(SELECTOR_PARAGRAPH_RANGE);
            var headings = paragraphControls.querySelector(SELECTOR_PARAGRAPH_HEADINGS);
            if (scope.value === "range") {
                range.removeAttribute("disabled");
                headings.removeAttribute("disabled");
            } else {
                range.setAttribute("disabled", "");
                headings.setAttribute("disabled", "");
            }
        }
    }

    /**
     * Initializes the dialog after it has loaded.
     */
    channel.on("foundation-contentloaded", function (e) {
        if (e.target.getElementsByClassName(CLASS_EDIT_DIALOG).length > 0) {
            Coral.commons.ready(e.target, function (dialog) {
                initialize(dialog);
            });
        }
    });

})(window, jQuery, jQuery(document), Granite, Coral);
