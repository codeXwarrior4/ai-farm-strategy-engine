import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  createFarmerProfile,
  getFarmerProfile,
  type FarmerProfileResponse,
  type CreateFarmerProfileRequest,
} from '../api/farmerProfile';
import { ApiError } from '../api/apiClient';

type FormState = 'idle' | 'submitting' | 'success' | 'error';

interface FormErrors {
  [field: string]: string;
}

/**
 * Farmer Profile Page — Phase 1.
 * Allows creating and retrieving a farmer profile via the backend API.
 * Shows loading, success and error states.
 * No fake data is displayed.
 */
export default function FarmerProfilePage() {
  const [userId, setUserId] = useState('');
  const [formState, setFormState] = useState<FormState>('idle');
  const [fieldErrors, setFieldErrors] = useState<FormErrors>({});
  const [globalError, setGlobalError] = useState('');
  const [createdProfile, setCreatedProfile] = useState<FarmerProfileResponse | null>(null);
  const [fetchedProfile, setFetchedProfile] = useState<FarmerProfileResponse | null>(null);
  const [fetchState, setFetchState] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [fetchError, setFetchError] = useState('');

  const [form, setForm] = useState<CreateFarmerProfileRequest>({
    fullName: '',
    phoneNumber: '',
    primaryState: '',
    primaryDistrict: '',
    totalLandHectares: undefined,
    yearsOfExperience: 0,
    preferredLanguage: 'en',
  });

  function handleChange(e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) {
    const { name, value } = e.target;
    setForm(prev => ({
      ...prev,
      [name]: name === 'yearsOfExperience' ? parseInt(value, 10) || 0
            : name === 'totalLandHectares' ? (value ? parseFloat(value) : undefined)
            : value,
    }));
    setFieldErrors(prev => ({ ...prev, [name]: '' }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!userId.trim()) {
      setGlobalError('Please enter a User ID before submitting.');
      return;
    }

    setFormState('submitting');
    setFieldErrors({});
    setGlobalError('');
    setCreatedProfile(null);

    try {
      const profile = await createFarmerProfile(parseInt(userId, 10), form);
      setCreatedProfile(profile);
      setFormState('success');
    } catch (error) {
      setFormState('error');
      if (error instanceof ApiError && error.data.fieldErrors) {
        setFieldErrors(error.data.fieldErrors);
        setGlobalError(error.data.message);
      } else if (error instanceof Error) {
        setGlobalError(error.message);
      } else {
        setGlobalError('An unexpected error occurred.');
      }
    }
  }

  async function handleFetchProfile() {
    if (!userId.trim()) {
      setFetchError('Please enter a User ID to fetch the profile.');
      return;
    }
    setFetchState('loading');
    setFetchError('');
    setFetchedProfile(null);

    try {
      const profile = await getFarmerProfile(parseInt(userId, 10));
      setFetchedProfile(profile);
      setFetchState('success');
    } catch (error) {
      setFetchState('error');
      if (error instanceof ApiError) {
        setFetchError(`${error.status}: ${error.data.message}`);
      } else if (error instanceof Error) {
        setFetchError(error.message);
      } else {
        setFetchError('An unexpected error occurred.');
      }
    }
  }

  const isSubmitting = formState === 'submitting';

  return (
    <div className="min-h-screen bg-slate-950 overflow-hidden">
      {/* Background */}
      <div className="fixed inset-0 pointer-events-none" aria-hidden="true">
        <div className="absolute top-[-20%] right-[-10%] w-[500px] h-[500px] bg-indigo-600/15 rounded-full blur-3xl" />
        <div className="absolute bottom-[-20%] left-[-10%] w-[400px] h-[400px] bg-purple-600/10 rounded-full blur-3xl" />
      </div>

      <div className="relative max-w-2xl mx-auto px-6 py-12">
        {/* Back link */}
        <Link
          to="/"
          className="inline-flex items-center gap-2 text-slate-400 hover:text-slate-200 text-sm mb-8 transition-colors"
        >
          ← Back to Status
        </Link>

        <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 to-purple-400 mb-2">
          Farmer Profile
        </h1>
        <p className="text-slate-400 text-sm mb-8">
          Phase 1 — Create or retrieve a farmer profile via the backend API.
        </p>

        {/* User ID input */}
        <div className="glass-card p-5 mb-6">
          <label htmlFor="userId-input" className="form-label">User ID (required for all operations)</label>
          <input
            id="userId-input"
            type="number"
            min="1"
            value={userId}
            onChange={e => setUserId(e.target.value)}
            className="form-input"
            placeholder="e.g. 1"
          />
          <p className="text-xs text-slate-500 mt-2">
            The user must exist in the database. Create a user directly in MySQL first (Phase 1 limitation — auth endpoints come in Phase 2).
          </p>
        </div>

        {/* Create Profile Form */}
        <section className="glass-card p-6 mb-6" aria-labelledby="create-form-heading">
          <h2 id="create-form-heading" className="text-lg font-semibold text-slate-200 mb-5">
            Create Profile
          </h2>

          <form id="create-profile-form" onSubmit={handleSubmit} noValidate>
            <div className="grid gap-4">
              {/* Full Name */}
              <div>
                <label htmlFor="fullName" className="form-label">
                  Full Name <span className="text-red-400">*</span>
                </label>
                <input
                  id="fullName"
                  name="fullName"
                  type="text"
                  value={form.fullName}
                  onChange={handleChange}
                  className="form-input"
                  placeholder="e.g. Ramu Kumar"
                  required
                />
                {fieldErrors.fullName && (
                  <p className="text-xs text-red-400 mt-1">{fieldErrors.fullName}</p>
                )}
              </div>

              {/* Phone Number */}
              <div>
                <label htmlFor="phoneNumber" className="form-label">Phone Number</label>
                <input
                  id="phoneNumber"
                  name="phoneNumber"
                  type="tel"
                  value={form.phoneNumber}
                  onChange={handleChange}
                  className="form-input"
                  placeholder="+919876543210"
                />
              </div>

              {/* State / District */}
              <div className="grid sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="primaryState" className="form-label">State</label>
                  <input
                    id="primaryState"
                    name="primaryState"
                    type="text"
                    value={form.primaryState}
                    onChange={handleChange}
                    className="form-input"
                    placeholder="e.g. Maharashtra"
                  />
                </div>
                <div>
                  <label htmlFor="primaryDistrict" className="form-label">District</label>
                  <input
                    id="primaryDistrict"
                    name="primaryDistrict"
                    type="text"
                    value={form.primaryDistrict}
                    onChange={handleChange}
                    className="form-input"
                    placeholder="e.g. Nashik"
                  />
                </div>
              </div>

              {/* Land / Experience */}
              <div className="grid sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="totalLandHectares" className="form-label">Total Land (hectares)</label>
                  <input
                    id="totalLandHectares"
                    name="totalLandHectares"
                    type="number"
                    min="0.0001"
                    step="0.01"
                    value={form.totalLandHectares ?? ''}
                    onChange={handleChange}
                    className="form-input"
                    placeholder="e.g. 2.5"
                  />
                </div>
                <div>
                  <label htmlFor="yearsOfExperience" className="form-label">
                    Years of Experience <span className="text-red-400">*</span>
                  </label>
                  <input
                    id="yearsOfExperience"
                    name="yearsOfExperience"
                    type="number"
                    min="0"
                    max="99"
                    value={form.yearsOfExperience}
                    onChange={handleChange}
                    className="form-input"
                  />
                  {fieldErrors.yearsOfExperience && (
                    <p className="text-xs text-red-400 mt-1">{fieldErrors.yearsOfExperience}</p>
                  )}
                </div>
              </div>

              {/* Language */}
              <div>
                <label htmlFor="preferredLanguage" className="form-label">
                  Preferred Language <span className="text-red-400">*</span>
                </label>
                <select
                  id="preferredLanguage"
                  name="preferredLanguage"
                  value={form.preferredLanguage}
                  onChange={handleChange}
                  className="form-input"
                >
                  <option value="en">English (en)</option>
                  <option value="hi">Hindi (hi)</option>
                  <option value="mr">Marathi (mr)</option>
                  <option value="gu">Gujarati (gu)</option>
                  <option value="pa">Punjabi (pa)</option>
                  <option value="ta">Tamil (ta)</option>
                  <option value="te">Telugu (te)</option>
                  <option value="kn">Kannada (kn)</option>
                </select>
              </div>
            </div>

            {/* Global error */}
            {globalError && (
              <div className="mt-4 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
                {globalError}
              </div>
            )}

            {/* Success */}
            {formState === 'success' && createdProfile && (
              <div className="mt-4 px-4 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm">
                ✓ Profile created! ID: {createdProfile.id} for {createdProfile.fullName}
              </div>
            )}

            <button
              id="submit-profile-btn"
              type="submit"
              disabled={isSubmitting}
              className="btn-primary w-full mt-5"
            >
              {isSubmitting ? 'Creating…' : 'Create Profile'}
            </button>
          </form>
        </section>

        {/* Retrieve Profile */}
        <section className="glass-card p-6" aria-labelledby="retrieve-heading">
          <h2 id="retrieve-heading" className="text-lg font-semibold text-slate-200 mb-4">
            Retrieve Profile
          </h2>
          <p className="text-sm text-slate-400 mb-4">
            Fetch the farmer profile associated with the User ID entered above.
          </p>

          <button
            id="fetch-profile-btn"
            onClick={handleFetchProfile}
            disabled={fetchState === 'loading'}
            className="btn-secondary w-full"
          >
            {fetchState === 'loading' ? 'Fetching…' : 'Get Profile by User ID'}
          </button>

          {fetchError && (
            <div className="mt-4 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
              {fetchError}
            </div>
          )}

          {fetchState === 'success' && fetchedProfile && (
            <div className="mt-4 bg-slate-900/60 rounded-xl border border-white/10 overflow-hidden">
              <div className="px-4 py-2 border-b border-white/10 text-xs text-slate-400 font-mono">
                Response — 200 OK
              </div>
              <pre className="px-4 py-4 text-xs text-slate-300 overflow-auto">
                {JSON.stringify(fetchedProfile, null, 2)}
              </pre>
            </div>
          )}
        </section>

        <footer className="mt-8 text-xs text-slate-600 text-center">
          Phase 1 · No AI predictions in this phase
        </footer>
      </div>
    </div>
  );
}
