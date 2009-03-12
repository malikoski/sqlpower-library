/*
 * Copyright (c) 2007, SQL Power Group Inc.
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of SQL Power Group Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package ca.sqlpower.swingui.table;

import java.math.BigDecimal;

import javax.swing.JLabel;

import ca.sqlpower.swingui.table.BaseRendererTest;
import ca.sqlpower.swingui.table.DecimalTableCellRenderer;

public class DecimalRendererTest extends BaseRendererTest {

    public void test1() {

        DecimalTableCellRenderer fmt = new DecimalTableCellRenderer();
        JLabel renderer = (JLabel) fmt.getTableCellRendererComponent(table, 1.2345, false, false, 0, 0);
        String renderedValue = renderer.getText();
        assertEquals("renderer formatted OK", "1.2", renderedValue);

        renderer = (JLabel) fmt.getTableCellRendererComponent(table, new BigDecimal(111111111.11), false, false, 0, 0);
        renderedValue = renderer.getText();
        // This test is not Locale-specific because the format is hard-coded in DecimalRenderer
        assertEquals("renderer formatted OK", "111,111,111.1", renderedValue);

        renderer = (JLabel) fmt.getTableCellRendererComponent(table, null, false, false, 0, 0);
        renderedValue = renderer.getText();
        assertEquals("renderer formatted OK", "null", renderedValue);
    }
}