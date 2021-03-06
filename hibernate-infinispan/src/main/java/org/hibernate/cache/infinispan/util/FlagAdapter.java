/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat, Inc. and/or its affiliates, and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.hibernate.cache.infinispan.util;
import org.hibernate.cache.CacheException;
import org.infinispan.context.Flag;

/**
 * FlagAdapter.
 * 
 * @author Galder Zamarreño
 * @since 3.5
 */
public enum FlagAdapter {
   ZERO_LOCK_ACQUISITION_TIMEOUT,
   CACHE_MODE_LOCAL,
   FORCE_ASYNCHRONOUS,
   FORCE_SYNCHRONOUS,
   SKIP_CACHE_STORE,
   SKIP_CACHE_LOAD;
   
   Flag toFlag() {
      switch(this) {
         case ZERO_LOCK_ACQUISITION_TIMEOUT:
            return Flag.ZERO_LOCK_ACQUISITION_TIMEOUT;
         case CACHE_MODE_LOCAL:
            return Flag.CACHE_MODE_LOCAL;
         case FORCE_ASYNCHRONOUS:
            return Flag.FORCE_ASYNCHRONOUS;
         case FORCE_SYNCHRONOUS:
            return Flag.FORCE_SYNCHRONOUS;
         case SKIP_CACHE_STORE:
            return Flag.SKIP_CACHE_STORE;
         case SKIP_CACHE_LOAD:
            return Flag.SKIP_CACHE_LOAD;
         default:
            throw new CacheException("Unmatched Infinispan flag " + this);
      }
   }
   
   static Flag[] toFlags(FlagAdapter[] adapters) {
      Flag[] flags = new Flag[adapters.length];
      for (int i = 0; i < adapters.length; i++) {
         flags[i] = adapters[i].toFlag();
      }
      return flags;
   }
}
