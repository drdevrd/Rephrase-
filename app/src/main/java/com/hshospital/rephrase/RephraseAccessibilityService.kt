<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0d1117">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="RePhrase Settings"
            android:textSize="22sp"
            android:textStyle="bold"
            android:textColor="#58a6ff"
            android:layout_marginBottom="24dp"/>

        <!-- API KEY -->
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="API Key" android:textColor="#c9d1d9" android:textSize="14sp"
            android:layout_marginBottom="6dp"/>
        <EditText
            android:id="@+id/apiKeyInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="Paste your API key here"
            android:textColor="#ffffff"
            android:textColorHint="#6e7681"
            android:background="@drawable/input_bg"
            android:padding="12dp"
            android:inputType="textVisiblePassword"
            android:layout_marginBottom="10dp"/>
        <Button
            android:id="@+id/saveApiKeyBtn"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="Save API Key"
            android:backgroundTint="#1f6feb"
            android:textAllCaps="false"
            android:layout_marginBottom="20dp"/>

        <!-- PROVIDER SELECTOR -->
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="AI Provider" android:textColor="#c9d1d9" android:textSize="14sp"
            android:layout_marginBottom="10dp"/>

        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal" android:layout_marginBottom="6dp">
            <RadioButton
                android:id="@+id/rbGemini"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:text="Gemini"
                android:textColor="#c9d1d9"
                android:buttonTint="#58a6ff"/>
            <RadioButton
                android:id="@+id/rbClaude"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:text="Claude"
                android:textColor="#c9d1d9"
                android:buttonTint="#58a6ff"/>
            <RadioButton
                android:id="@+id/rbOpenAI"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:text="OpenAI"
                android:textColor="#c9d1d9"
                android:buttonTint="#58a6ff"/>
        </LinearLayout>

        <Button
            android:id="@+id/saveProviderBtn"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="Save Provider"
            android:backgroundTint="#238636"
            android:textAllCaps="false"
            android:layout_marginBottom="24dp"/>

        <!-- ACCESSIBILITY SETTINGS -->
        <Button
            android:id="@+id/accessibilityBtn"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="Open Accessibility Settings"
            android:backgroundTint="#6e40c9"
            android:textAllCaps="false"
            android:layout_marginBottom="24dp"/>

        <!-- ASK AI PROMPT -->
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Ask AI - Custom Question" android:textColor="#c9d1d9" android:textSize="14sp"
            android:layout_marginBottom="6dp"/>
        <EditText
            android:id="@+id/askAiPromptInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="e.g. Summarise this text in 2 lines"
            android:textColor="#ffffff"
            android:textColorHint="#6e7681"
            android:background="@drawable/input_bg"
            android:padding="12dp"
            android:layout_marginBottom="10dp"/>
        <Button
            android:id="@+id/saveAskAiBtn"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="Save Question"
            android:backgroundTint="#0e7490"
            android:textAllCaps="false"
            android:layout_marginBottom="24dp"/>

        <!-- CUSTOM TONES -->
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Custom Tone 1" android:textColor="#c9d1d9" android:textSize="14sp"
            android:layout_marginBottom="6dp"/>
        <EditText android:id="@+id/customName1" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:hint="Button name (e.g. Empathetic)"
            android:textColor="#ffffff" android:textColorHint="#6e7681"
            android:background="@drawable/input_bg" android:padding="12dp" android:layout_marginBottom="6dp"/>
        <EditText android:id="@+id/customPrompt1" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:hint="Instruction (e.g. Rewrite with empathy)"
            android:textColor="#ffffff" android:textColorHint="#6e7681"
            android:background="@drawable/input_bg" android:padding="12dp" android:layout_marginBottom="8dp"/>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal" android:layout_marginBottom="20dp">
            <Button android:id="@+id/saveCustom1" android:layout_width="0dp" android:layout_weight="1"
                android:layout_height="48dp" android:text="Save" android:backgroundTint="#c05800"
                android:textAllCaps="false" android:layout_marginEnd="8dp"/>
            <Button android:id="@+id/deleteCustom1" android:layout_width="0dp" android:layout_weight="1"
                android:layout_height="48dp" android:text="Delete" android:backgroundTint="#6e1c1c"
                android:textAllCaps="false"/>
        </LinearLayout>

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Custom Tone 2" android:textColor="#c9d1d9" android:textSize="14sp"
            android:layout_marginBottom="6dp"/>
        <EditText android:id="@+id/customName2" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:hint="Button name"
            android:textColor="#ffffff" android:textColorHint="#6e7681"
            android:background="@drawable/input_bg" android:padding="12dp" android:layout_marginBottom="6dp"/>
        <EditText android:id="@+id/customPrompt2" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:hint="Instruction"
            android:textColor="#ffffff" android:textColorHint="#6e7681"
            android:background="@drawable/input_bg" android:padding="12dp" android:layout_marginBottom="8dp"/>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal" android:layout_marginBottom="20dp">
            <Button android:id="@+id/saveCustom2" android:layout_width="0dp" android:layout_weight="1"
                android:layout_height="48dp" android:text="Save" android:backgroundTint="#c05800"
                android:textAllCaps="false" android:layout_marginEnd="8dp"/>
            <Button android:id="@+id/deleteCustom2" android:layout_width="0dp" android:layout_weight="1"
                android:layout_height="48dp" android:text="Delete" android:backgroundTint="#6e1c1c"
                android:textAllCaps="false"/>
        </LinearLayout>

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Custom Tone 3" android:textColor="#c9d1d9" android:textSize="14sp"
            android:layout_marginBottom="6dp"/>
        <EditText android:id="@+id/customName3" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:hint="Button name"
            android:textColor="#ffffff" android:textColorHint="#6e7681"
            android:background="@drawable/input_bg" android:padding="12dp" android:layout_marginBottom="6dp"/>
        <EditText android:id="@+id/customPrompt3" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:hint="Instruction"
            android:textColor="#ffffff" android:textColorHint="#6e7681"
            android:background="@drawable/input_bg" android:padding="12dp" android:layout_marginBottom="8dp"/>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal" android:layout_marginBottom="20dp">
            <Button android:id="@+id/saveCustom3" android:layout_width="0dp" android:layout_weight="1"
                android:layout_height="48dp" android:text="Save" android:backgroundTint="#c05800"
                android:textAllCaps="false" android:layout_marginEnd="8dp"/>
            <Button android:id="@+id/deleteCustom3" android:layout_width="0dp" android:layout_weight="1"
                android:layout_height="48dp" android:text="Delete" android:backgroundTint="#6e1c1c"
                android:textAllCaps="false"/>
        </LinearLayout>

    </LinearLayout>
</ScrollView>
