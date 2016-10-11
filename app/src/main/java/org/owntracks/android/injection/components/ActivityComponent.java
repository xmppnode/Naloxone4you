package org.owntracks.android.injection.components;

import org.owntracks.android.injection.modules.ActivityModule;
import org.owntracks.android.injection.modules.ViewModelModule;
import org.owntracks.android.injection.scopes.PerActivity;
import org.owntracks.android.ui.contacts.ContactsActivity;
import org.owntracks.android.ui.map.MapActivity;
import org.owntracks.android.ui.status.StatusActivity;

import dagger.Component;

/* Copyright 2016 Patrick Löwenstein
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
@PerActivity
@Component(dependencies = AppComponent.class, modules = {ActivityModule.class, ViewModelModule.class})
public interface ActivityComponent {
    void inject(MapActivity activity);
    void inject(ContactsActivity activity);
    void inject(StatusActivity activity);
}
