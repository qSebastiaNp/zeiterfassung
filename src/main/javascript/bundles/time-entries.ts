import "../components/time-entry-slot-form";
import "../components/time-entry-datepicker";
import "../components/time-entry-duration-input";
import "../components/time-entry-element";
import "../components/time-entry-user-search";

// Ensure TimeEntrySlotForm is loaded and available globally
import TimeEntrySlotForm from "../components/time-entry-slot-form/TimeEntrySlotForm";
(window as any).TimeEntrySlotForm = TimeEntrySlotForm;
