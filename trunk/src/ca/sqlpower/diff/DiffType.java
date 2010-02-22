/*
 * Copyright (c) 2008, SQL Power Group Inc.
 *
 * This file is part of Power*Architect.
 *
 * Power*Architect is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Power*Architect is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */
package ca.sqlpower.diff;

public enum DiffType {
	LEFTONLY,
	MODIFIED,      // Does not mean that the modifications are big enough to warrant generating new SQL script
	SQL_MODIFIED,  // Similar to modified, but primarily for columns to generate altering SQL script. Also used to modify table remarks	   
	SAME,		// Some implementations may not use this.
	RIGHTONLY,
	KEY_CHANGED,	// primary key changed or removed
	DROP_KEY;       // the key with this type needs to be dropped
}
