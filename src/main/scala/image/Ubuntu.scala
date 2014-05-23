// Copyright (C) 2013  Carl Pulley
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <http://www.gnu.org/licenses/>.

package cakesolutions

package image

import org.jclouds.compute.domain.OsFamily

// Basic Ubuntu instance that we wish to provision (here we are cloud infrastructure agnostic)
abstract class Ubuntu(version: String) extends Image {
  template_builder
    .osFamily(OsFamily.UBUNTU)
    .osVersionMatches(version)
    .smallest()

  ports += 22 // SSH

  chef_runlist
    .addRecipe("apt")
}
