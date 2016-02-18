/*
 * Copyright 2016 Open Networking Laboratory
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
 */

package org.onosproject.yangutils.parser.impl.listeners;

import org.onosproject.yangutils.parser.antlrgencode.GeneratedYangParser;
import org.onosproject.yangutils.parser.impl.TreeWalkListener;

/*
 * Reference: RFC6020 and YANG ANTLR Grammar
 *
 * ABNF grammar as per RFC6020
 * units-stmt          = units-keyword sep string optsep stmtend
 *
 * ANTLR grammar rule
 * unitsStatement : UNITS_KEYWORD string STMTEND;
 */

/**
 * Implements listener based call back function corresponding to the "units"
 * rule defined in ANTLR grammar file for corresponding ABNF rule in RFC 6020.
 */
public final class UnitsListener {

    /**
     * Creates a new units listener.
     */
    private UnitsListener() {
    }

    /**
     * It is called when parser receives an input matching the grammar
     * rule (units), performs validation and updates the data model
     * tree.
     *
     * @param listener listener's object.
     * @param ctx context object of the grammar rule.
     */
    public static void processUnitsEntry(TreeWalkListener listener,
                                           GeneratedYangParser.UnitsStatementContext ctx) {
        // TODO method implementation
    }

    /**
     * It is called when parser exits from grammar rule (units), it performs
     * validation and updates the data model tree.
     *
     * @param listener listener's object.
     * @param ctx context object of the grammar rule.
     */
    public static void processUnitsExit(TreeWalkListener listener,
                                          GeneratedYangParser.UnitsStatementContext ctx) {
        // TODO method implementation
    }
}