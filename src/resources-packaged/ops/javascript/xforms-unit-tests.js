ORBEON.testcases = {


    dateTimeTestCase: new YAHOO.tool.TestCase({

        name: "Library doing conversions between different formats of date and time",
        opsXFormsProperties: {},

        setUp: function() {
            this.opsXFormsProperties = window.opsXFormsProperties;
        },

        tearDown: function() {
            window.opsXFormsProperties = this.opsXFormsProperties;
        },

        testMagicTimeSimple: function() {
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("11:22:33");
            YAHOO.util.Assert.areEqual(11, jsDate.getHours());
            YAHOO.util.Assert.areEqual(22, jsDate.getMinutes());
            YAHOO.util.Assert.areEqual(33, jsDate.getSeconds());
        },

        testMagicTime24h: function() {
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("20:22:33");
            YAHOO.util.Assert.areEqual(20, jsDate.getHours());
        },

        testMagicTimeUSHour: function() {
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("9:05 pm");
            YAHOO.util.Assert.areEqual(21, jsDate.getHours());
        },

        testMagicTimeInvalid: function() {
            YAHOO.util.Assert.isNull(ORBEON.util.DateTime.magicTimeToJSDate("gaga"));
        },

        testMagicDateSimple: function() {
            var jsDate = ORBEON.util.DateTime.magicDateToJSDate("4/25/1998");
            YAHOO.util.Assert.areEqual(4, jsDate.getMonth() + 1);
            YAHOO.util.Assert.areEqual(25, jsDate.getDate());
            YAHOO.util.Assert.areEqual(1998, jsDate.getFullYear());
        },

        testDisplayTimeUSMorning: function() {
            window.opsXFormsProperties = {};
            window.opsXFormsProperties[FORMAT_INPUT_TIME_PROPERTY] = "[h]:[m]:[s] [P]";
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("11:22:33");
            var displayTime = ORBEON.util.DateTime.jsDateToformatDisplayTime(jsDate);
            YAHOO.util.Assert.areEqual("11:22:33 a.m.", displayTime);
        },

        testDisplayTimeUSAfternoon: function() {
            window.opsXFormsProperties = {};
            window.opsXFormsProperties[FORMAT_INPUT_TIME_PROPERTY] = "[h]:[m]:[s] [P]";
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("16:22:33");
            var displayTime = ORBEON.util.DateTime.jsDateToformatDisplayTime(jsDate);
            YAHOO.util.Assert.areEqual("4:22:33 p.m.", displayTime);
        },

        testDisplayDate: function() {
            var jsDate = ORBEON.util.DateTime.magicDateToJSDate("4/25/1998");
            var displayDate = ORBEON.util.DateTime.jsDateToformatDisplayDate(jsDate);
            YAHOO.util.Assert.areEqual("4/25/1998", displayDate);
        },

        testToISOTime: function() {
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate("20:22:33");
            YAHOO.util.Assert.areEqual("20:22:33", ORBEON.util.DateTime.jsDateToISOTime(jsDate));
        },

        testToISODate: function() {
            var jsDate = ORBEON.util.DateTime.magicDateToJSDate("4/25/1998");
            YAHOO.util.Assert.areEqual("1998-04-25", ORBEON.util.DateTime.jsDateToISODate(jsDate));
        }

    }),

    inputTestCase: new YAHOO.tool.TestCase({

        name: "XForms Input",

        setUp: function() {
            // Save properties as they are at the beginning of a test.
            this.opsXFormsProperties = window.opsXFormsProperties;
            //ORBEON.testing.restoreInstance(this);
        },

        tearDown: function() {
            // Restore properties
            window.opsXFormsProperties = this.opsXFormsProperties;
        },

        testSimpleInput: function() {
            ORBEON.testing.executeWithInitialInstance(this, function() {
                ORBEON.testing.executeCausingAjaxRequest(this, function() {
                    ORBEON.xforms.Document.setValue("input-type", "string");
                    ORBEON.xforms.Document.setValue("input-field", "foo");
                }, function() {
                    YAHOO.util.Assert.areEqual("foo", ORBEON.xforms.Document.getValue("input-field"));
                });
            });
        },

        // Test for bug: click on date field, (Calendar opens), click again => date is replaced in ISO format
        testClickOnDate: function() {
            ORBEON.testing.executeWithInitialInstance(this, function() {
                var dateControl = ORBEON.util.Dom.getElementById("input-date");
                var dateInput = ORBEON.util.Dom.getChildElementByIndex(dateControl, 0);
                var valueBefore = dateInput.value;
                dateInput.focus();
                YAHOO.util.UserAction.click(dateInput);
                YAHOO.util.UserAction.mousedown(dateInput); // Simulate mousedown as this is the event the calendar is listening on
                YAHOO.util.Assert.areEqual(valueBefore, dateInput.value);
            });
        },

        // Change input type from string to date
        testStringToDate: function() {
            ORBEON.testing.executeWithInitialInstance(this, function() {
                ORBEON.testing.executeCausingAjaxRequest(this, function() {
                    ORBEON.xforms.Document.setValue("input-type", "date");
                }, function() {
                    var dateControl = ORBEON.util.Dom.getElementById("input-field");
                    var firstInput = ORBEON.util.Dom.getChildElementByIndex(dateControl, 0);
                    var secondInput = ORBEON.util.Dom.getChildElementByIndex(dateControl, 1);
                    YAHOO.util.Assert.isTrue(ORBEON.util.Dom.hasClass(dateControl, "xforms-type-date"));
                    YAHOO.util.Assert.isTrue(ORBEON.util.Dom.hasClass(firstInput, "xforms-type-date"));
                    YAHOO.util.Assert.isNull(secondInput);
                });
            });
        },

        // Change input type from string to dateTime
        testStringToDateTime: function() {
            ORBEON.testing.executeWithInitialInstance(this, function() {
                ORBEON.testing.executeCausingAjaxRequest(this, function() {
                    ORBEON.xforms.Document.setValue("input-type", "date-time");
                    ORBEON.xforms.Document.setValue("input-field", "1997-05-19T21:02:13");
                }, function() {
                    var control = ORBEON.util.Dom.getElementById("input-field");
                    var firstInput = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                    var secondInput = ORBEON.util.Dom.getChildElementByIndex(control, 1);
                    YAHOO.util.Assert.isTrue(ORBEON.util.Dom.hasClass(control, "xforms-type-dateTime"));
                    YAHOO.util.Assert.isTrue(ORBEON.util.Dom.hasClass(firstInput, "xforms-type-date"));
                    YAHOO.util.Assert.isTrue(ORBEON.util.Dom.hasClass(secondInput, "xforms-type-time"));
                    YAHOO.util.Assert.areEqual("5/19/1997", firstInput.value);
                    YAHOO.util.Assert.areEqual("9:02:13 p.m.", secondInput.value);
                });
            });
        },

        // Change input type from dateTime to time
        testDataTimeToTime: function() {
            ORBEON.testing.executeWithInitialInstance(this, function() {
                ORBEON.testing.executeCausingAjaxRequest(this, function() {
                    // Change to dateTime
                    ORBEON.xforms.Document.setValue("input-type", "date-time");
                }, function() {
                    // Check it is dateTime
                    var control = ORBEON.util.Dom.getElementById("input-field");
                    YAHOO.util.Assert.isTrue(ORBEON.util.Dom.hasClass(control, "xforms-type-dateTime"));
                    ORBEON.testing.executeCausingAjaxRequest(this, function() {
                        ORBEON.xforms.Document.setValue("input-type", "time");
                        ORBEON.xforms.Document.setValue("input-field", "21:02:13");
                    }, function() {
                        var firstInput = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                        YAHOO.util.Assert.isTrue(ORBEON.util.Dom.hasClass(control, "xforms-type-time"));
                        YAHOO.util.Assert.areEqual("9:02:13 p.m.", firstInput.value);
                    });
                });
            });
        }
    })

};

ORBEON.testing = {

    // Tests that rely on instances having a certain value should start by callng this utility function
    executeWithInitialInstance: function(testCase, testFunction) {
        ORBEON.testing.executeCausingAjaxRequest(testCase, function() {
            ORBEON.xforms.Document.dispatchEvent("main-model", "restore-instance");
        }, function() {
            testFunction.call(testCase);
        });
    },

    executeCausingAjaxRequest: function(testCase, causingAjaxRequestFunction, afterAjaxResponseFunction) {
        
        function ajaxReceived() {
            testCase.resume(function() {
                ORBEON.xforms.Events.ajaxResponseProcessedEvent.unsubscribe(ajaxReceived);
                afterAjaxResponseFunction.call(testCase);
            });
        }

        ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(ajaxReceived, testCase, true);
        causingAjaxRequestFunction.call(testCase);
        testCase.wait();
    },

    run: function() {
        var testLogger = new YAHOO.tool.TestLogger();
        for (var testcaseID in ORBEON.testcases)
            YAHOO.tool.TestRunner.add(ORBEON.testcases[testcaseID]);
        YAHOO.tool.TestRunner.run();
    }
}